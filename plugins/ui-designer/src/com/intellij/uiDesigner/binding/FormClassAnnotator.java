/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.UIBundle;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class FormClassAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance(FormClassAnnotator.class);

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof PsiField) {
      PsiField field = (PsiField) psiElement;
      final PsiFile boundForm = FormReferenceProvider.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
    else if (psiElement instanceof PsiClass) {
      PsiClass aClass = (PsiClass) psiElement;
      final List<PsiFile> formsBoundToClass = FormClassIndex.findFormsBoundToClass(aClass.getProject(), aClass);
      if (formsBoundToClass.size() > 0) {
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
      holder.newAnnotation(HighlightSeverity.WARNING, message).range(field.getInitializer())
      .withFix(new IntentionAction() {
        @Override
        @NotNull
        public String getText() {
          return message;
        }

        @Override
        @NotNull
        public String getFamilyName() {
          return UIBundle.message("remove.field.initializer.quick.fix");
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return field.getInitializer() != null;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          final PsiExpression initializer = field.getInitializer();
          LOG.assertTrue(initializer != null);
          initializer.delete();
        }

        @Override
        public boolean startInWriteAction() {
          return true;
        }
      }).create();
    }
  }
}
