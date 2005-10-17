package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiMethodCallExpression;

/**
 * @author dsl
 */
class MethodCallUsageInfo extends UsageInfo {
  private final PsiMethodCallExpression myMethodCall;

  public MethodCallUsageInfo(PsiMethodCallExpression methodCall) {
    super(methodCall);
    myMethodCall = methodCall;
  }

  public PsiMethodCallExpression getMethodCall() {
    return myMethodCall;
  }
}
