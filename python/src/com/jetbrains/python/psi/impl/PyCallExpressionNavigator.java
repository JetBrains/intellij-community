package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyCallExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyCallExpressionNavigator {
  private PyCallExpressionNavigator() {
  }

  @Nullable
  public static PyCallExpression getPyCallExpressionByCallee(final PsiElement element){
     final PsiElement parent = element.getParent();
    if (parent instanceof PyCallExpression){
      final PyCallExpression expression = (PyCallExpression)parent;
      return expression.getCallee() == element ? expression : null;
    }
    return null;
  }
}
