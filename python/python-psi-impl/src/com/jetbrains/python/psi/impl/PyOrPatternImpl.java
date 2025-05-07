package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyOrPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyOrPatternImpl extends PyElementImpl implements PyOrPattern {
  public PyOrPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyOrPattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    return PyUnionType.union(ContainerUtil.map(getAlternatives(), it -> context.getType(it)));
  }
}
