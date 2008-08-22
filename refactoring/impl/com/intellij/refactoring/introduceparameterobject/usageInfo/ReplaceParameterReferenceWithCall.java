package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceParameterReferenceWithCall extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String newParameterName;
  private final String parameterGetterName;

  public ReplaceParameterReferenceWithCall(PsiReferenceExpression element, String newParameterName, String parameterGetterName) {
    super(element);
    this.parameterGetterName = parameterGetterName;
    this.newParameterName = newParameterName;
    expression = element;
  }

  public void fixUsage() throws IncorrectOperationException {
    final String newExpression = newParameterName + '.' + parameterGetterName + "()";
    if (expression.isValid()) {
      MutationUtils.replaceExpression(newExpression, expression);
    }
  }

}
