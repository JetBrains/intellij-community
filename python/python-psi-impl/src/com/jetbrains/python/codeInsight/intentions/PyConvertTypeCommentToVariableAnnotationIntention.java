// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.google.common.collect.ImmutableMap;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@ApiStatus.Internal
public final class PyConvertTypeCommentToVariableAnnotationIntention extends PsiUpdateModCommandAction<PsiElement> {
  public PyConvertTypeCommentToVariableAnnotationIntention() {
    super(PsiElement.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiComment typeComment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
    if (typeComment != null) {
      final SmartPointerManager manager = SmartPointerManager.getInstance(context.project());
      final SmartPsiElementPointer<PsiComment> commentPointer = manager.createSmartPsiElementPointer(typeComment);
      final Map<PyTargetExpression, String> map = mapTargetsToAnnotations(typeComment);
      if (!map.isEmpty()) {
        if (typeComment.getParent() instanceof PyAssignmentStatement && map.size() == 1) {
          final PyTargetExpression target = ContainerUtil.getFirstItem(map.keySet());
          assert target != null;
          PyTypeHintGenerationUtil.insertVariableAnnotation(target, false, new AnnotationInfo(map.get(target)), null);
        }
        else {
          for (Map.Entry<PyTargetExpression, String> entry : new ArrayList<>(map.entrySet())) {
            PyTypeHintGenerationUtil.insertVariableAnnotation(entry.getKey(), false, new AnnotationInfo(entry.getValue()), null);
          }
        }

        final PsiComment staleComment = commentPointer.getElement();
        if (staleComment != null) {
          staleComment.delete();
        }
      }
    }
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.convert.type.comment.to.variable.annotation");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile instanceof PyFile && LanguageLevel.forElement(psiFile).isAtLeast(LanguageLevel.PYTHON36)) {
      final PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
      if (comment != null && isSuitableTypeComment(comment)) {
        return Presentation.of(PyPsiBundle.message("INTN.convert.type.comment.to.variable.annotation"));
      }
    }
    return null;
  }

  private static boolean isSuitableTypeComment(@NotNull PsiComment comment) {
    final String annotation = PyTypingTypeProvider.getTypeCommentValue(comment.getText());
    return annotation != null && !mapTargetsToAnnotations(comment).isEmpty();
  }

  private static @NotNull Map<PyTargetExpression, String> mapTargetsToAnnotations(@NotNull PsiComment typeComment) {
    final PsiElement parent = typeComment.getParent();
    if (parent instanceof PyAssignmentStatement assignment) {
      final PyExpression[] rawTargets = assignment.getRawTargets();
      if (rawTargets.length == 1) {
        return mapTargetsToAnnotations(rawTargets[0], typeComment);
      }
    }
    else if (parent instanceof PyForPart forPart) {
      final PyExpression target = forPart.getTarget();
      if (target != null) {
        return mapTargetsToAnnotations(target, typeComment);
      }
    }
    else if (parent instanceof PyWithStatement) {
      final PyWithItem[] withItems = ((PyWithStatement)parent).getWithItems();
      if (withItems.length == 1) {
        final PyExpression target = withItems[0].getTarget();
        if (target != null) {
          return mapTargetsToAnnotations(target, typeComment);
        }
      }
    }
    return Collections.emptyMap();
  }

  private static @NotNull Map<PyTargetExpression, String> mapTargetsToAnnotations(@NotNull PyExpression targetExpr,
                                                                                  @NotNull PsiComment typeComment) {
    final PyTargetExpression firstTarget = PsiTreeUtil.findChildOfType(targetExpr, PyTargetExpression.class, false);
    if (firstTarget == null || firstTarget.getTypeComment() != typeComment) {
      return Collections.emptyMap();
    }

    final String annotation = PyTypingTypeProvider.getTypeCommentValue(typeComment.getText());
    if (annotation != null) {
      if (targetExpr instanceof PyTargetExpression) {
        return ImmutableMap.of((PyTargetExpression)targetExpr, annotation);
      }

      final PyElementGenerator generator = PyElementGenerator.getInstance(targetExpr.getProject());
      final PyExpression parsed;
      try {
        parsed = generator.createExpressionFromText(LanguageLevel.PYTHON36, annotation);
      }
      catch (IncorrectOperationException e) {
        return Collections.emptyMap();
      }
      final Map<PyTargetExpression, PyExpression> targetToExpr = PyTypingTypeProvider.mapTargetsToAnnotations(targetExpr, parsed);
      return EntryStream.of(targetToExpr).mapValues(PyExpression::getText).toCustomMap(LinkedHashMap::new);
    }
    return Collections.emptyMap();
  }
}
