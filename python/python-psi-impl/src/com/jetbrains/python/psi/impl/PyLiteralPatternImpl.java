package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLiteralPattern;
import org.jetbrains.annotations.NotNull;

public class PyLiteralPatternImpl extends PyElementImpl implements PyLiteralPattern {
  public PyLiteralPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLiteralPattern(this);
  }

  @Override
  public @NotNull PyExpression getExpression() {
    return findNotNullChildByClass(PyExpression.class);
  }

  @Override
  public boolean isIrrefutable() {
    return false;
  }
}
