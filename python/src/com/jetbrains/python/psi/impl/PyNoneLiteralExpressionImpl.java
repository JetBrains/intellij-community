package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PyNoneLiteralExpressionImpl extends PyElementImpl implements PyNoneLiteralExpression {
  public PyNoneLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return PyNoneType.INSTANCE;
  }
}
