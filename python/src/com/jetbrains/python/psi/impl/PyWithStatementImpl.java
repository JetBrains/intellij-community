package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyWithStatement;

/**
 * @author yole
 */
public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement {
  public PyWithStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithStatement(this);
  }
}
