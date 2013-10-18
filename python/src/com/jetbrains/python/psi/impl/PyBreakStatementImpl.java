package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
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
    return getLoopStatement(this);
  }

  @Nullable
  private static PyLoopStatement getLoopStatement(@NotNull PsiElement element) {
    final PyLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyLoopStatement.class);
    if (loop instanceof PyStatementWithElse) {
      final PyStatementWithElse stmt = (PyStatementWithElse)loop;
      final PyElsePart elsePart = stmt.getElsePart();
      if (PsiTreeUtil.isAncestor(elsePart, element, true)) {
        return getLoopStatement(loop);
      }
    }
    return loop;
  }
}
