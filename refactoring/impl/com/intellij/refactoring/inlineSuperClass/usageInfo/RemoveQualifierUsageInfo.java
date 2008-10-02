/*
 * User: anna
 * Date: 02-Oct-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class RemoveQualifierUsageInfo extends FixableUsageInfo {
  private final PsiReferenceExpression myExpression;

  public RemoveQualifierUsageInfo(final PsiReferenceExpression expression) {
    super(expression);
    myExpression = expression;
  }


  public void fixUsage() throws IncorrectOperationException {
    myExpression.getQualifierExpression().delete();
  }
}