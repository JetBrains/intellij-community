package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyDoubleStarPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyDoubleStarPatternImpl extends PyElementImpl implements PyDoubleStarPattern {
  public PyDoubleStarPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDoubleStarPattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    return null;
  }
}
