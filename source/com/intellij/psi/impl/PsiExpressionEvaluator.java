package com.intellij.psi.impl;

import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nullable;

public class PsiExpressionEvaluator implements ConstantExpressionEvaluator {

  public Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow) {
    return expression instanceof PsiExpression ? JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)expression, throwExceptionOnOverflow) : null;
  }

  public Object computeExpression(PsiElement expression, boolean throwExceptionOnOverflow, @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    return expression instanceof PsiExpression ? JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)expression, null, throwExceptionOnOverflow, auxEvaluator) : null;
  }
}
