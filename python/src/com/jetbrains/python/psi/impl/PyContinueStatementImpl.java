package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyLoopStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyContinueStatementImpl extends PyElementImpl implements PyContinueStatement {
  public PyContinueStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyContinueStatement(this);
  }

  @Nullable
  public PyLoopStatement getLoopStatement() {
    return PsiTreeUtil.getParentOfType(this, PyLoopStatement.class);
  }
}
