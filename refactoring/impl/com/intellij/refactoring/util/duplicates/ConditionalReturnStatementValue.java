package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class ConditionalReturnStatementValue implements ReturnValue {
  PsiExpression myReturnValue;

  public ConditionalReturnStatementValue(final PsiExpression returnValue) {
    myReturnValue = returnValue;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof ConditionalReturnStatementValue)) return false;
    PsiExpression otherReturnValue = ((ConditionalReturnStatementValue) other).myReturnValue;
    if (otherReturnValue == null || myReturnValue == null) return myReturnValue == null && otherReturnValue == null;
    return PsiEquivalenceUtil.areElementsEquivalent(myReturnValue, otherReturnValue);
  }

  public PsiStatement createReplacement(PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
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
