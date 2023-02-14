package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyMatchStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyMatchStatementImpl extends PyElementImpl implements PyMatchStatement {
  public PyMatchStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyMatchStatement(this);
  }

  @Override
  public @Nullable PyExpression getSubject() {
    return findChildByClass(PyExpression.class);
  }

  @Override
  public @NotNull List<PyCaseClause> getCaseClauses() {
    return findChildrenByType(PyElementTypes.CASE_CLAUSE);
  }
}
