package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyAsPattern;
import com.jetbrains.python.psi.PyElementVisitor;

public class PyAsPatternImpl extends PyElementImpl implements PyAsPattern {
  public PyAsPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAsPattern(this);
  }
}
