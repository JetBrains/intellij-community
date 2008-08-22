package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceParameterAssignmentWithCall extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String newParameterName;
  private final String setterName;
  private final String getterName;

  public ReplaceParameterAssignmentWithCall(PsiReferenceExpression element, String newParameterName, String setterName, String getterName) {
    super(element);
    this.setterName = setterName;
    this.getterName = getterName;
    this.newParameterName = newParameterName;
    expression = element;
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class);
    assert assignment != null;
    final PsiExpression rhs = assignment.getRExpression();
    if (rhs == null) {
      return;
    }
    final String rhsText = rhs.getText();
    final String operator = assignment.getOperationSign().getText();
    final String newExpression;
    if ("=".equals(operator)) {
      newExpression = newParameterName + '.' + setterName + '(' + rhsText + ')';
    }
    else {
      final String strippedOperator = operator.substring(0, operator.length() - 1);
      newExpression =
        newParameterName + '.' + setterName + '(' + newParameterName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
    }
    MutationUtils.replaceExpression(newExpression, assignment);
  }

}
