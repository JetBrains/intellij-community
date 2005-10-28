package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.uiDesigner.ReferenceUtil;
import org.jetbrains.annotations.NonNls;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.10.2005
 * Time: 15:11:01
 * To change this template use File | Settings | File Templates.
 */
public class FormClassAnnotator implements ApplicationComponent, Annotator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.binding.FormClassAnnotator");

  private static final String FIELD_IS_OVERWRITTEN = JavaErrorMessages.message("uidesigned.field.is.overwritten.by.generated.code");
  private static final String BOUND_FIELD_TYPE_MISMATCH = JavaErrorMessages.message("uidesigner.bound.field.type.mismatch");

  @SuppressWarnings({"UNUSED_SYMBOL"})
  public FormClassAnnotator(final FileTypeManager fileTypeManager) {
    // dependency ensures that we get created after FileTypeManager and StdFileTypes.JAVA already exists
  }

  @NonNls
  public String getComponentName() {
    return "FormClassAnnotator";
  }

  public void initComponent() {
    StdFileTypes.JAVA.getLanguage().injectAnnotator(this);
  }

  public void disposeComponent() {
    StdFileTypes.JAVA.getLanguage().removeAnnotator(this);
  }

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    if (psiElement instanceof PsiField) {
      PsiField field = (PsiField) psiElement;
      final PsiFile boundForm = CodeInsightUtil.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
  }

  private void annotateFormField(final PsiField field, final PsiFile boundForm, final AnnotationHolder holder) {
    final List<IntentionAction> options = new ArrayList<IntentionAction>();

    LOG.assertTrue(boundForm instanceof PsiPlainTextFile);
    final PsiType guiComponentType = ReferenceUtil.getGUIComponentType((PsiPlainTextFile)boundForm, field.getName());
    if (guiComponentType != null) {
      final PsiType fieldType = field.getType();
      if (!fieldType.isAssignableFrom(guiComponentType)) {
        String message = MessageFormat.format(BOUND_FIELD_TYPE_MISMATCH, guiComponentType.getCanonicalText(), fieldType.getCanonicalText());
        Annotation annotation = holder.createErrorAnnotation(field.getTypeElement(), message);
        annotation.registerFix(new ChangeFormComponentTypeFix((PsiPlainTextFile)boundForm, field.getName(), field.getType()), null, options);
        annotation.registerFix(new ChangeBoundFieldTypeFix(field, guiComponentType), null, options);
      }
    }

    if (field.hasInitializer()) {
      String message = MessageFormat.format(FIELD_IS_OVERWRITTEN, field.getName());
      Annotation annotation = holder.createWarningAnnotation(field.getInitializer(), message);
      annotation.registerFix(new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.UNUSED_SYMBOL), options), null, options);
    }
  }
}
