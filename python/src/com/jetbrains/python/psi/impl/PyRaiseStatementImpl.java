package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.Nullable;

/**
 * Describes 'raise' statement.
 */
public class PyRaiseStatementImpl extends PyElementImpl implements PyRaiseStatement {
  public PyRaiseStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyRaiseStatement(this);
  }

  @Nullable
  public PyExpression[] getExpressions() {
    return PsiTreeUtil.getChildrenOfType(this, PyExpression.class);
  }
}
