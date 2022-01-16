package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyGroupPattern;
import com.jetbrains.python.psi.PyPattern;
import org.jetbrains.annotations.NotNull;

public class PyGroupPatternImpl extends PyElementImpl implements PyGroupPattern {
  public PyGroupPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyGroupPattern(this);
  }

  @Override
  public @NotNull PyPattern getPattern() {
    return findNotNullChildByClass(PyPattern.class);
  }

  @Override
  public boolean isIrrefutable() {
    return getPattern().isIrrefutable();
  }
}
