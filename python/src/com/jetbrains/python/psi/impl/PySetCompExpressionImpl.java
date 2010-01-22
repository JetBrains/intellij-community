package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PySetCompExpressionImpl extends PyComprehensionElementImpl implements PySetCompExpression {
  public PySetCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    // TODO
    return null;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySetCompExpression(this);
  }
}
