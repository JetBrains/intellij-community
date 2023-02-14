package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPatternArgumentList;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PyClassPatternImpl extends PyElementImpl implements PyClassPattern {
  public PyClassPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClassPattern(this);
  }

  @Override
  public @NotNull PyReferenceExpression getClassNameReference() {
    return Objects.requireNonNull(findChildByClass(PyReferenceExpression.class));
  }

  @Override
  public @NotNull PyPatternArgumentList getArgumentList() {
    return Objects.requireNonNull(findChildByClass(PyPatternArgumentList.class));
  }

  @Override
  public boolean isIrrefutable() {
    return false;
  }
}
