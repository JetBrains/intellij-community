/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class InsertNewFix implements IntentionAction {
  private final PsiMethodCallExpression myMethodCall;
  private final PsiClass myClass;

  public InsertNewFix(PsiMethodCallExpression methodCall, PsiClass aClass) {
    myMethodCall = methodCall;
    myClass = aClass;
  }

  public String getText() {
    return "Insert new";
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethodCall != null
    && myMethodCall.isValid()
    && myMethodCall.getManager().isInProject(myMethodCall);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(myMethodCall.getContainingFile())) return;
    final PsiElementFactory factory = myMethodCall.getManager().getElementFactory();
    final PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText("new X()",null);

    newExpression.getClassReference().replace(factory.createClassReferenceElement(myClass));
    newExpression.getArgumentList().replace(myMethodCall.getArgumentList().copy());
    myMethodCall.replace(newExpression);
  }

  public boolean startInWriteAction() {
    return true;
  }
}