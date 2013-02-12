package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyEmptyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyEmptyExpressionImpl extends PyElementImpl implements PyEmptyExpression {
  public PyEmptyExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return null;
  }
}
