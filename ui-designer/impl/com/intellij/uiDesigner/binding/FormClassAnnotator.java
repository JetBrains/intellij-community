package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.UIBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

/**
 * @author yole
 */
public class FormClassAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.binding.FormClassAnnotator");

  private static final String FIELD_IS_OVERWRITTEN = JavaErrorMessages.message("uidesigned.field.is.overwritten.by.generated.code");
  private static final String BOUND_FIELD_TYPE_MISMATCH = JavaErrorMessages.message("uidesigner.bound.field.type.mismatch");

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    if (psiElement instanceof PsiField) {
      PsiField field = (PsiField) psiElement;
      final PsiFile boundForm = FormReferenceProvider.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
    else if (psiElement instanceof PsiClass) {
      PsiClass aClass = (PsiClass) psiElement;
      final String qName = aClass.getQualifiedName();
      if (qName != null) {
        final PsiFile[] formsBoundToClass = JavaPsiFacade.getInstance(aClass.getProject()).findFormsBoundToClass(qName);
        if (formsBoundToClass.length > 0) {
          Annotation boundClassAnnotation = holder.createInfoAnnotation(aClass.getNameIdentifier(), null);
          boundClassAnnotation.setGutterIconRenderer(new BoundIconRenderer(aClass));
        }
      }
    }
  }

  private static void annotateFormField(final PsiField field, final PsiFile boundForm, final AnnotationHolder holder) {
    Annotation boundFieldAnnotation = holder.createInfoAnnotation(field, null);
    boundFieldAnnotation.setGutterIconRenderer(new BoundIconRenderer(field));

    LOG.assertTrue(boundForm instanceof PsiPlainTextFile);
    final PsiType guiComponentType = FormReferenceProvider.getGUIComponentType((PsiPlainTextFile)boundForm, field.getName());
    if (guiComponentType != null) {
      final PsiType fieldType = field.getType();
      if (!fieldType.isAssignableFrom(guiComponentType)) {
        String message = MessageFormat.format(BOUND_FIELD_TYPE_MISMATCH, guiComponentType.getCanonicalText(), fieldType.getCanonicalText());
        Annotation annotation = holder.createErrorAnnotation(field.getTypeElement(), message);
        annotation.registerFix(new ChangeFormComponentTypeFix((PsiPlainTextFile)boundForm, field.getName(), field.getType()), null, null, null);
        annotation.registerFix(new ChangeBoundFieldTypeFix(field, guiComponentType), null, null, null);
      }
    }

    if (field.hasInitializer()) {
      final String message = MessageFormat.format(FIELD_IS_OVERWRITTEN, field.getName());
      Annotation annotation = holder.createWarningAnnotation(field.getInitializer(), message);
      annotation.registerFix(new IntentionAction() {
        @NotNull
        public String getText() {
          return message;
        }

        @NotNull
        public String getFamilyName() {
          return UIBundle.message("remove.field.initializer.quick.fix");
        }

        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return field.getInitializer() != null;
        }

        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          final PsiExpression initializer = field.getInitializer();
          LOG.assertTrue(initializer != null);
          initializer.delete();
        }

        public boolean startInWriteAction() {
          return true;
        }
      });
    }
  }
}
