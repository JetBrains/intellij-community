package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiReferenceExpression;

/**
 * @author ven
 */
public class InternalUsageInfo extends UsageInfo {
  private final PsiReferenceExpression myReferenceExpression;

  public InternalUsageInfo(final PsiReferenceExpression referenceExpression) {
    super(referenceExpression);
    myReferenceExpression = referenceExpression;
  }

  public PsiReferenceExpression getReferenceExpression() {
    return myReferenceExpression;
  }
}
