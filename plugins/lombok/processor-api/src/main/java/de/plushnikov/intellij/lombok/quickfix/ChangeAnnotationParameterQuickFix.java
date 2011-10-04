package de.plushnikov.intellij.lombok.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class ChangeAnnotationParameterQuickFix implements IntentionAction, LocalQuickFix {
  private final PsiAnnotation myAnnotation;
  private final String myName;
  private final String myNewValue;

  public ChangeAnnotationParameterQuickFix(@NotNull PsiAnnotation psiAnnotation, @NotNull String name) {
    this(psiAnnotation, name, null);
  }

  public ChangeAnnotationParameterQuickFix(@NotNull PsiAnnotation psiAnnotation, @NotNull String name, @Nullable String newValue) {
    myAnnotation = psiAnnotation;
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
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    applyFixInner(project);
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    applyFixInner(project);
  }

  private void applyFixInner(final Project project) {
    final PsiFile file = myAnnotation.getContainingFile();
    final Editor editor = CodeInsightUtil.positionCursor(project, file, myAnnotation);
    if (editor != null) {
      new WriteCommandAction(project, file) {
        protected void run(Result result) throws Throwable {
          final PsiNameValuePair valuePair = selectAnnotationAttribute();

          if (null != valuePair) {
            // delete this parameter
            valuePair.delete();
          }

          if (null != myNewValue) {
            //add new parameter
            final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myAnnotation.getProject()).getElementFactory();
            PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText("@" + myAnnotation.getQualifiedName() + "(" + myName + "=" + myNewValue + ")", myAnnotation.getContext());
            final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();

            myAnnotation.setDeclaredAttributeValue(attributes[0].getName(), attributes[0].getValue());
          }

          UndoUtil.markPsiFileForUndo(file);
        }

        @Override
        protected boolean isGlobalUndoAction() {
          return true;
        }
      }.execute();
    }
  }

  private PsiNameValuePair selectAnnotationAttribute() {
    PsiNameValuePair result = null;
    PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      @NonNls final String attributeName = attribute.getName();
      if (Comparing.equal(myName, attributeName) || attributeName == null && myName.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
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
