package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyIfStatementImpl extends PyPartitionedElementImpl implements PyIfStatement {
  public PyIfStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyIfStatement(this);
  }

  @NotNull
  public PyIfPart getIfPart() {
    return (PyIfPart)getPartNotNull(PyElementTypes.IF_PART_IF);
  }

  @NotNull
  public PyIfPart[] getElifParts() {
    return childrenToPsi(PyElementTypes.ELIFS, PyIfPart.EMPTY_ARRAY);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }
}
