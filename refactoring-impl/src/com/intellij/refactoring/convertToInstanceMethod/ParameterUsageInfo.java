package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;

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
