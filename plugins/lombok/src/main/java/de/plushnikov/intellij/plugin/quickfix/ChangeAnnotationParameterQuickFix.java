package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameValuePair;
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

  @NotNull
  public String getText() {
    if (null == myNewValue) {
      return String.format("Remove annotation parameter '%s'", myName);
    } else {
      return String.format("Set annotation parameter '%s = %s'", myName, myNewValue);
    }
  }

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

  public boolean startInWriteAction() {
    return false;
  }

}
