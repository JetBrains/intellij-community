package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;

public class PyCaseClauseImpl extends PyElementImpl implements PyCaseClause {
  public PyCaseClauseImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public @NotNull PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCaseClause(this);
  }
}
