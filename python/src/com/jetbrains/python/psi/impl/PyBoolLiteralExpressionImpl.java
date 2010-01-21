package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PyBoolLiteralExpressionImpl extends PyElementImpl implements PyBoolLiteralExpression {
  public PyBoolLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    // TODO
    return null;
  }
}
