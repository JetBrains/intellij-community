package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElementVisitor;

public class PyClassPatternImpl extends PyElementImpl implements PyClassPattern {
  public PyClassPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClassPattern(this);
  }
}
