package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.ui.UIBundle;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

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
  private FormReferencesSearcher myRefSearcher;

  @SuppressWarnings({"UNUSED_SYMBOL"})
  public FormClassAnnotator(final FileTypeManager fileTypeManager) {
    // dependency ensures that we get created after FileTypeManager and StdFileTypes.JAVA already exists
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "FormClassAnnotator";
  }

  public void initComponent() {
    StdFileTypes.JAVA.getLanguage().injectAnnotator(this);
    myRefSearcher = new FormReferencesSearcher();
    ReferencesSearch.INSTANCE.registerExecutor(myRefSearcher);
    CacheBuilderRegistry.getInstance().registerCacheBuilder(StdFileTypes.GUI_DESIGNER_FORM, new FormWordsScanner());
  }

  public void disposeComponent() {
    StdFileTypes.JAVA.getLanguage().removeAnnotator(this);
    ReferencesSearch.INSTANCE.unregisterExecutor(myRefSearcher);
  }

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    if (psiElement instanceof PsiField) {
      PsiField field = (PsiField) psiElement;
      final PsiFile boundForm = CodeInsightUtil.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
    else if (psiElement instanceof PsiClass) {
      PsiClass aClass = (PsiClass) psiElement;
      final String qName = aClass.getQualifiedName();
      if (qName != null) {
        final PsiFile[] formsBoundToClass = psiElement.getManager().getSearchHelper().findFormsBoundToClass(qName);
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
    final PsiType guiComponentType = ReferenceUtil.getGUIComponentType((PsiPlainTextFile)boundForm, field.getName());
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
        public String getText() {
          return message;
        }

        public String getFamilyName() {
          return UIBundle.message("remove.field.initializer.quick.fix");
        }

        public boolean isAvailable(Project project, Editor editor, PsiFile file) {
          return field.getInitializer() != null;
        }

        public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
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
