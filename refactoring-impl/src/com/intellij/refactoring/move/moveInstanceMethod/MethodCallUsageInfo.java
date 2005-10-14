package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiMethodCallExpression;

/**
 * @author ven
 */
public class MethodCallUsageInfo extends UsageInfo {
  private PsiMethodCallExpression myMethodCallExpression;
  public MethodCallUsageInfo(final PsiReferenceExpression referenceExpression) {
    super(referenceExpression);
    myMethodCallExpression = (PsiMethodCallExpression)referenceExpression.getParent();
  }

  public PsiMethodCallExpression getMethodCallExpression() {
    return myMethodCallExpression;
  }
}
