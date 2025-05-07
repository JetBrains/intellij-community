package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyLiteralPatternImpl extends PyElementImpl implements PyLiteralPattern {
  public PyLiteralPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLiteralPattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    PyType literalType = PyLiteralType.Companion.fromLiteralParameter(getExpression(), context);
    if (literalType != null) {
      return literalType;
    }
    return context.getType(getExpression());
  }
}
