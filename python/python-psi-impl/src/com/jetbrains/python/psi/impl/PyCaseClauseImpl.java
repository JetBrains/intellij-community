package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyElementVisitor;

public class PyCaseClauseImpl extends PyElementImpl implements PyCaseClause {
  public PyCaseClauseImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCaseClause(this);
  }
}
