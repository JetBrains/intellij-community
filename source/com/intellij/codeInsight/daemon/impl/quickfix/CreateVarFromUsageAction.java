/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 14, 2002
 * Time: 5:15:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;

public abstract class CreateVarFromUsageAction extends CreateFromUsageBaseAction {
  protected final PsiReferenceExpression myReferenceExpression;

  public CreateVarFromUsageAction(PsiReferenceExpression referenceElement) {
    myReferenceExpression = referenceElement;
  }

  protected boolean isValidElement(PsiElement element) {
    PsiReferenceExpression expression = (PsiReferenceExpression) element;
    return CreateFromUsageUtils.isValidReference(expression);
  }

  protected PsiElement getElement() {
    if (!myReferenceExpression.isValid() || !myReferenceExpression.getManager().isInProject(myReferenceExpression)) return null;

    final PsiElement parent = myReferenceExpression.getParent();

    if (parent instanceof PsiMethodCallExpression) return null;

    if (myReferenceExpression.getReferenceNameElement() != null) {
      if (!CreateFromUsageUtils.isValidReference(myReferenceExpression)) {
        return myReferenceExpression;
      }
    }

    return null;
  }

  protected boolean isAvailableImpl(int offset) {
    if (shouldShowTag(offset, myReferenceExpression.getReferenceNameElement(), myReferenceExpression)) {
      setText(getText(myReferenceExpression.getReferenceName()));
      return true;
    }

    return false;
  }

  protected abstract String getText(String varName);
}
