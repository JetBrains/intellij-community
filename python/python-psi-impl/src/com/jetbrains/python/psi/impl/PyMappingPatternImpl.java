package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyMappingPattern;

public class PyMappingPatternImpl extends PyElementImpl implements PyMappingPattern {
  public PyMappingPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyMappingPattern(this);
  }

  @Override
  public boolean isIrrefutable() {
    return false;
  }
}
