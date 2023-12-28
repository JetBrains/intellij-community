package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyCapturePattern;
import com.jetbrains.python.psi.PyElementVisitor;

public class PyCapturePatternImpl extends PyElementImpl implements PyCapturePattern {
  public PyCapturePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCapturePattern(this);
  }
}
