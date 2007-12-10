package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
class ParameterUsageInfo extends UsageInfo {
  private PsiReferenceExpression myReferenceExpression;

  public ParameterUsageInfo(PsiReferenceExpression refereneceElement) {
    super(refereneceElement);
    myReferenceExpression = refereneceElement;
  }

  public PsiJavaCodeReferenceElement getReferenceExpression() {
    return myReferenceExpression;
  }
}
