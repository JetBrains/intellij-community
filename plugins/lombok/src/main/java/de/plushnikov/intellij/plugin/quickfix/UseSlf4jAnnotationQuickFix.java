package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor.getLoggerName;

public class UseSlf4jAnnotationQuickFix extends PsiUpdateModCommandAction<PsiClass> {

  private final @NotNull SmartPsiElementPointer<PsiField> elementToRemove;

  public UseSlf4jAnnotationQuickFix(@NotNull PsiField elementToRemove, @NotNull PsiClass containingClass) {
    super(containingClass);
    this.elementToRemove = SmartPointerManager.getInstance(elementToRemove.getProject()).createSmartPsiElementPointer(elementToRemove);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass element, @NotNull ModPsiUpdater updater) {

    final PsiField field = elementToRemove.getElement();
    if (field != null) {
      final Collection<PsiReference> all = ReferencesSearch.search(field).findAll();
      List<PsiElement> refs = ContainerUtil.map(all, ref -> updater.getWritable(ref.getElement()));
      PsiField writableField = updater.getWritable(field);

      final String loggerName = getLoggerName(element);
      for (PsiElement refElement : refs) {
        PsiReference ref = refElement.getReference();
        if (ref != null) {
          ref.handleElementRename(loggerName);
        }
      }

      writableField.delete();

      JavaCodeStyleManager.getInstance(context.project()).removeRedundantImports((PsiJavaFile)element.getContainingFile());
    }
    JavaCodeStyleManager.getInstance(context.project())
      .shortenClassReferences(Objects.requireNonNull(element.getModifierList()).addAnnotation(LombokClassNames.SLF_4_J));
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("intention.family.name.slf4j.annotation");
  }
}
