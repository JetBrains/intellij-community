package com.intellij.psi.impl;

import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ConstantExpressionUtil;

import java.util.Set;

/**
 * @author ven
 */
public class PsiConstantEvaluationHelperImpl extends PsiConstantEvaluationHelper {
  public Object computeConstantExpression(PsiExpression expression) {
    return computeConstantExpression(expression, false);
  }

  public Object computeConstantExpression(PsiExpression expression, boolean throwExceptionOnOverflow) {
    return ConstantExpressionEvaluator.computeConstantExpression(expression, null, throwExceptionOnOverflow);
  }

  public static Object computeCastTo(PsiExpression expression, PsiType castTo, Set visitedVars) {
    Object value = ConstantExpressionEvaluator.computeConstantExpression(expression, visitedVars, false);
    if(value == null) return null;
    return ConstantExpressionUtil.computeCastTo(value, castTo);
  }
}
