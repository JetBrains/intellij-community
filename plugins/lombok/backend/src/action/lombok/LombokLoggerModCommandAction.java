// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok;

import com.intellij.lombok.shared.LombokBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModShowConflicts;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.lombok.backend.action.lombok.BaseLombokModCommandAction.isValidForFile;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.addAnnotation;

final class LombokLoggerModCommandAction extends PsiUpdateModCommandAction<PsiClass> {
  LombokLoggerModCommandAction(@NotNull PsiClass elementClass) {
    super(elementClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("action.defaultLombokLogger.text");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
    return isValidForFile(context, element) ? super.getPresentation(context, element) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass element, @NotNull ModPsiUpdater updater) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return;
    var originalClass = PsiTreeUtil.findSameElementInCopy(element, updater.getOriginalFile(containingFile));
    processClass(originalClass, updater);
  }

  private static void processClass(@NotNull PsiClass psiClass, @NotNull ModPsiUpdater updater) {
    LombokProcessorManager manager = LombokProcessorManager.getInstance();
    final Collection<AbstractLogProcessor> logProcessors = Arrays.asList(
      manager.getCommonsLogProcessor(),
      manager.getJBossLogProcessor(),
      manager.getLog4jProcessor(),
      manager.getLog4j2Processor(),
      manager.getLogProcessor(),
      manager.getSlf4jProcessor(),
      manager.getXSlf4jProcessor(),
      manager.getFloggerProcessor(),
      manager.getCustomLogProcessor());

    final String lombokLoggerName = AbstractLogProcessor.getLoggerName(psiClass);
    final boolean lombokLoggerIsStatic = AbstractLogProcessor.isLoggerStatic(psiClass);
    List<LoggerField> loggerFields = new ArrayList<>();
    Set<PsiField> seenLoggerFields = new HashSet<>();
    Map<PsiElement, ModShowConflicts.Conflict> conflicts = new LinkedHashMap<>();

    for (AbstractLogProcessor logProcessor : logProcessors) {
      String loggerType = logProcessor.getLoggerType(psiClass); // null when the custom log's declaration is invalid
      if (loggerType == null) {
        continue;
      }
      for (PsiField psiField : psiClass.getFields()) {
        if (psiField.getType().equalsToText(loggerType) && seenLoggerFields.add(psiField)) {
          if (!isValidLoggerField(psiField, lombokLoggerName, lombokLoggerIsStatic)) {
            String message = LombokBundle.message("dialog.message.logger.field.s.not.private.sfinal.field.named.s.refactor.anyway",
                                                  psiField.getName(), lombokLoggerIsStatic ? 1 : 0, lombokLoggerName);
            conflicts.put(psiField, new ModShowConflicts.Conflict(List.of(message)));
          }
          List<PsiReference> references = lombokLoggerName.equals(psiField.getName())
                                          ? List.of()
                                          : new ArrayList<>(ReferencesSearch.search(psiField).findAll());
          loggerFields.add(new LoggerField(psiField, logProcessor, references));
        }
      }
    }

    updater.showConflicts(conflicts);

    List<WritableLoggerField> writableLoggerFields = new ArrayList<>();
    for (LoggerField loggerField : loggerFields) {
      PsiField writableField = updater.getWritable(loggerField.field());
      List<PsiReference> writableReferences = new ArrayList<>();
      for (PsiReference reference : loggerField.references()) {
        var writableReference = findWritableReference(reference, updater.getWritable(reference.getElement()));
        if (writableReference == null) {
          updater.cancel(LombokBundle.message("dialog.message.logger.cannot.update.references"));
          return;
        }
        writableReferences.add(writableReference);
      }
      writableLoggerFields.add(new WritableLoggerField(writableField, loggerField.logProcessor(), writableReferences));
    }

    for (WritableLoggerField loggerField : writableLoggerFields) {
      if (!processLoggerField(loggerField, lombokLoggerName)) {
        updater.cancel(LombokBundle.message("dialog.message.logger.cannot.update.references"));
        return;
      }
    }
  }

  private static @Nullable PsiReference findWritableReference(@NotNull PsiReference reference, @NotNull PsiElement writableElement) {
    return ContainerUtil.find(writableElement.getReferences(),
                              candidate -> candidate.getRangeInElement().equals(reference.getRangeInElement()));
  }

  private static boolean processLoggerField(@NotNull WritableLoggerField loggerField,
                                            @NotNull String lombokLoggerName) {
    PsiField psiField = loggerField.field();
    if (!lombokLoggerName.equals(psiField.getName())) {
      var outerFieldReferences = collectOuterFieldReferences(lombokLoggerName, psiField);
      psiField.setName(lombokLoggerName);
      for (PsiReference reference : loggerField.references()) {
        var renamed = reference.handleElementRename(lombokLoggerName);
        if (renamed instanceof PsiReferenceExpression expression &&
            expression.resolve() instanceof PsiVariable resolved && !psiField.isEquivalentTo(resolved)) {
          var qualifier = ExpressionUtils.getEffectiveQualifier(expression, psiField);
          if (qualifier == null) return false;
          expression.setQualifierExpression(qualifier);
        }
      }
      for (var entry : outerFieldReferences.entrySet()) {
        var expression = entry.getKey();
        var field = entry.getValue();
        if (!field.isEquivalentTo(expression.resolve())) {
          var qualifier = ExpressionUtils.getEffectiveQualifier(expression, field);
          if (qualifier == null) return false;
          expression.setQualifierExpression(qualifier);
        }
      }
    }

    addAnnotation(Objects.requireNonNull(psiField.getContainingClass()), loggerField.logProcessor().getSupportedAnnotationClasses()[0]);

    psiField.delete();
    return true;
  }

  private static @NotNull Map<PsiReferenceExpression, PsiField> collectOuterFieldReferences(@NotNull String lombokLoggerName,
                                                                                            PsiField psiField) {
    PsiClass psiClass = psiField.getContainingClass();
    var references = new LinkedHashMap<PsiReferenceExpression, PsiField>();
    if (psiClass == null) return references;
    psiClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (!expression.isQualified() && lombokLoggerName.equals(expression.getReferenceName()) &&
            expression.resolve() instanceof PsiField field && PsiTreeUtil.isAncestor(field.getContainingClass(), psiClass, true)) {
          references.put(expression, field);
        }
      }
    });
    return references;
  }

  private record LoggerField(@NotNull PsiField field,
                             @NotNull AbstractLogProcessor logProcessor,
                             @NotNull List<@NotNull PsiReference> references) {
  }

  private record WritableLoggerField(@NotNull PsiField field,
                                     @NotNull AbstractLogProcessor logProcessor,
                                     @NotNull List<@NotNull PsiReference> references) {
  }

  private static boolean isValidLoggerField(@NotNull PsiField psiField, @NotNull String lombokLoggerName, boolean lombokLoggerIsStatic) {
    boolean isPrivate = psiField.hasModifierProperty(PsiModifier.PRIVATE);
    boolean isStatic = lombokLoggerIsStatic == psiField.hasModifierProperty(PsiModifier.STATIC);
    boolean isFinal = psiField.hasModifierProperty(PsiModifier.FINAL);
    boolean isProperlyNamed = lombokLoggerName.equals(psiField.getName());

    return isPrivate && isStatic && isFinal && isProperlyNamed;
  }
}
