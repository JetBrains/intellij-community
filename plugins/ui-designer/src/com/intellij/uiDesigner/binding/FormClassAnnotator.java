// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.ui.UIBundle;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;


public class FormClassAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance(FormClassAnnotator.class);

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof PsiField field) {
      final PsiFile boundForm = FormReferenceProvider.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
    else if (psiElement instanceof PsiClass aClass) {
      final List<PsiFile> formsBoundToClass = FormClassIndex.findFormsBoundToClass(aClass.getProject(), aClass);
      if (!formsBoundToClass.isEmpty()) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(aClass.getNameIdentifier()).gutterIconRenderer(new BoundIconRenderer(aClass)).create();
      }
    }
  }

  private static void annotateFormField(final PsiField field, final PsiFile boundForm, final AnnotationHolder holder) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).gutterIconRenderer(new BoundIconRenderer(field)).create();

    LOG.assertTrue(boundForm instanceof PsiPlainTextFile);
    final PsiType guiComponentType = FormReferenceProvider.getGUIComponentType((PsiPlainTextFile)boundForm, field.getName());
    if (guiComponentType != null) {
      final PsiType fieldType = field.getType();
      if (!fieldType.isAssignableFrom(guiComponentType)) {
        String message = UIDesignerBundle.message("bound.field.type.mismatch", guiComponentType.getCanonicalText(),
                                                  fieldType.getCanonicalText());
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(field.getTypeElement())
        .withFix(new ChangeFormComponentTypeFix((PsiPlainTextFile)boundForm, field.getName(), field.getType()))
        .withFix(new ChangeBoundFieldTypeFix(field, guiComponentType)).create();
      }
    }

    if (field.hasInitializer()) {
      final String message = UIDesignerBundle.message("field.is.overwritten.by.generated.code", field.getName());
      PsiExpression initializer = Objects.requireNonNull(field.getInitializer());
      holder.newAnnotation(HighlightSeverity.WARNING, message).range(initializer)
        .withFix(new DeleteElementFix(initializer, UIBundle.message("remove.field.initializer.quick.fix"))).create();
    }
  }
}
