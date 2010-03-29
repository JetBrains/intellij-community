package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPrintStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyPrintStatementImpl extends PyElementImpl implements PyPrintStatement {
  public PyPrintStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
   protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPrintStatement(this);
  }
}
