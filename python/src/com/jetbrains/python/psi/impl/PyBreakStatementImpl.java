package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyLoopStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyBreakStatementImpl extends PyElementImpl implements PyBreakStatement {
  public PyBreakStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBreakStatement(this);
  }

  @Nullable
  public PyLoopStatement getLoopStatement() {
    return PsiTreeUtil.getParentOfType(this, PyLoopStatement.class);
  }
}
