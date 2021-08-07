package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyDoubleStarPattern;
import com.jetbrains.python.psi.PyElementVisitor;

public class PyDoubleStarPatternImpl extends PyElementImpl implements PyDoubleStarPattern {
  public PyDoubleStarPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDoubleStarPattern(this);
  }
}
