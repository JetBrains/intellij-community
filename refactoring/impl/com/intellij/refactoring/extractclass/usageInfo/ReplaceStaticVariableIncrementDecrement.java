package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableIncrementDecrement extends FixableUsageInfo {
  private final PsiExpression reference;
  private final String setterName;
  private final String getterName;
  private final String originalClassName;
  private final boolean isPublic;

  public ReplaceStaticVariableIncrementDecrement(PsiExpression reference,
                                                 String originalClassName,
                                                 String setterName,
                                                 String getterName,
                                                 boolean isPublic) {
    super(reference);
    this.getterName = getterName;
    this.setterName = setterName;
    this.originalClassName = originalClassName;
    this.isPublic = isPublic;
    final PsiPrefixExpression prefixExpr = PsiTreeUtil.getParentOfType(reference, PsiPrefixExpression.class);
    if (prefixExpr != null) {
      this.reference = prefixExpr;
    }
    else {
      this.reference = PsiTreeUtil.getParentOfType(reference, PsiPostfixExpression.class);
    }
  }

  public void fixUsage() throws IncorrectOperationException {

    final PsiJavaToken sign = reference instanceof PsiPrefixExpression
                              ? ((PsiPrefixExpression)reference).getOperationSign()
                              : ((PsiPostfixExpression)reference).getOperationSign();
    final String operator = sign.getText();
    if (isPublic) {
      MutationUtils.replaceExpression(originalClassName + '.' + reference.getText(), reference);
    }
    else {
      final String strippedOperator = getStrippedOperator(operator);
      final String newExpression =
        originalClassName + '.' + setterName + '(' + originalClassName + '.' + getterName + "()" + strippedOperator + "1)";
      MutationUtils.replaceExpression(newExpression, reference);
    }
  }

  private static String getStrippedOperator(String operator) {
    return operator.substring(0, operator.length() - 1);
  }
}
