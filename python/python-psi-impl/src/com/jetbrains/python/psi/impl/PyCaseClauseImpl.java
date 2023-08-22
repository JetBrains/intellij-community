package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyCaseClauseImpl extends PyElementImpl implements PyCaseClause {
  public PyCaseClauseImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCaseClause(this);
  }

  @Override
  public @Nullable PyPattern getPattern() {
    return findChildByClass(PyPattern.class);
  }

  @Override
  public @Nullable PyExpression getGuardCondition() {
    return findChildByClass(PyExpression.class);
  }

  @Override
  public @NotNull PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }
}
