package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyBinaryExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyBinaryExpressionNavigator {
  private PyBinaryExpressionNavigator() {
  }

  @Nullable
  public static PyBinaryExpression getBinaryExpressionByOperand(final PsiElement element) {
    final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    if (expression == null){
      return null;
    }
    if (expression.getPsiOperator() == element){
      return expression;
    }
    return null;
  }
}
