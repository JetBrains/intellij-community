package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySequencePattern;

public class PySequencePatternImpl extends PyElementImpl implements PySequencePattern {
  public PySequencePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySequencePattern(this);
  }

  @Override
  public boolean isIrrefutable() {
    return false;
  }
}
