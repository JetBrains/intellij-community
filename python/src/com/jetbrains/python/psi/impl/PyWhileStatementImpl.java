package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyWhileStatementImpl extends PyPartitionedElementImpl implements PyWhileStatement {
  public PyWhileStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWhileStatement(this);
  }

  @NotNull
  public PyWhilePart getWhilePart() {
    return (PyWhilePart)getPartNotNull(PyElementTypes.WHILE_PART);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }
}
