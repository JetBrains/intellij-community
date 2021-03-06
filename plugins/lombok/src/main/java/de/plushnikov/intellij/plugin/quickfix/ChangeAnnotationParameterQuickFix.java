package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Plushnikov Michail
 */
public class ChangeAnnotationParameterQuickFix extends LocalQuickFixOnPsiElement implements IntentionAction {
  private final String myName;
  private final String myNewValue;

  public ChangeAnnotationParameterQuickFix(@NotNull PsiAnnotation psiAnnotation, @NotNull String name, @Nullable String newValue) {
    super(psiAnnotation);
    myName = name;
    myNewValue = newValue;
  }

  @Override
  @NotNull
  @IntentionName
  public String getText() {
    if (null == myNewValue) {
      return LombokBundle.message("intention.name.remove.annotation.parameter.s", myName);
    } else {
      return LombokBundle.message("intention.name.set.annotation.parameter.s.s", myName, myNewValue);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    applyFix();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiAnnotation myAnnotation = (PsiAnnotation) startElement;
    final Editor editor = CodeInsightUtil.positionCursor(project, psiFile, myAnnotation);
    if (editor != null) {
      WriteCommandAction.writeCommandAction(project, psiFile).run(() ->
        {
          final PsiNameValuePair valuePair = selectAnnotationAttribute(myAnnotation);

          if (null != valuePair) {
            // delete this parameter
            valuePair.delete();
          }

          if (null != myNewValue) {
            //add new parameter
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myAnnotation.getProject());
            PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText("@" + myAnnotation.getQualifiedName() + "(" + myName + "=" + myNewValue + ")", myAnnotation.getContext());
            final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();

            myAnnotation.setDeclaredAttributeValue(attributes[0].getName(), attributes[0].getValue());
          }

          UndoUtil.markPsiFileForUndo(psiFile);
        }
      );
    }
  }

  private PsiNameValuePair selectAnnotationAttribute(PsiAnnotation psiAnnotation) {
    PsiNameValuePair result = null;
    PsiNameValuePair[] attributes = psiAnnotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      @NonNls final String attributeName = attribute.getName();
      if (Objects.equals(myName, attributeName) || attributeName == null && myName.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
        result = attribute;
        break;
      }
    }
    return result;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
