package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class DeleteMethodBodyFix implements IntentionAction {
  private PsiMethod myMethod;

  public DeleteMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  public String getText() {
    return "Delete Body";
  }

  public String getFamilyName() {
    return "Delete Body";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() && myMethod.getManager().isInProject(myMethod) && myMethod.getBody() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myMethod.getBody().delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
