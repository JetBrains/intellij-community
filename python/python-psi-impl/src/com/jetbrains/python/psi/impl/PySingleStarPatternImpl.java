package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySingleStarPattern;

public class PySingleStarPatternImpl extends PyElementImpl implements PySingleStarPattern {
  public PySingleStarPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySingleStarPattern(this);
  }
}
