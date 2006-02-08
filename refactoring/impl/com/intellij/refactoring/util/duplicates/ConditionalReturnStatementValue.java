package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.Comparing;
import com.intellij.codeInsight.PsiEquivalenceUtil;

/**
 * @author ven
 */
public class ConditionalReturnStatementValue implements ReturnValue {
  PsiExpression myReturnValue;

  public ConditionalReturnStatementValue(final PsiExpression returnValue) {
    myReturnValue = returnValue;
  }

  public boolean isEquivalent(ReturnValue other) {
    return other instanceof ConditionalReturnStatementValue;
  }

  public PsiStatement createReplacement(PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = methodCallExpression.getManager().getElementFactory();
    PsiIfStatement statement;
    if (myReturnValue == null) {
      statement = (PsiIfStatement)elementFactory.createStatementFromText("if(a) return;", null);
    }
    else {
      statement = (PsiIfStatement)elementFactory.createStatementFromText("if(a) return b;", null);
      final PsiReturnStatement thenBranch = (PsiReturnStatement)statement.getThenBranch();
      assert thenBranch != null;
      final PsiExpression returnValue = thenBranch.getReturnValue();
      assert returnValue != null;
      returnValue.replace(myReturnValue);
    }

    final PsiExpression condition = statement.getCondition();
    assert condition != null;
    condition.replace(methodCallExpression);
    return (PsiStatement)statement.getManager().getCodeStyleManager().reformat(statement);
  }
}
