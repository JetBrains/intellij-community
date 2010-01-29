package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PySetLiteralExpressionImpl extends PyElementImpl implements PySetLiteralExpression {
  public PySetLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return null;   // TODO
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySetLiteralExpression(this);
  }
}
