package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyCaptureContext;
import com.jetbrains.python.psi.PyCapturePattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyCapturePatternImpl extends PyElementImpl implements PyCapturePattern {
  public PyCapturePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCapturePattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    return PyCaptureContext.getCaptureType(this, context);
  }
}
