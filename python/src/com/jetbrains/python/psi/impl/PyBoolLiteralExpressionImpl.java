package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyBoolLiteralExpressionImpl extends PyElementImpl implements PyBoolLiteralExpression {
  public PyBoolLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    // TODO
    return null;
  }
}
