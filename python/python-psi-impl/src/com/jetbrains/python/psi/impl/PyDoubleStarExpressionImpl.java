// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PyDoubleStarExpressionImpl extends PyElementImpl implements PyDoubleStarExpression, PyInstantTypeProvider {
  public PyDoubleStarExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    // A `**mapping` unpacking is not a value, so it has no type of its own.
    return PyAnyType.getUnknown();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor visitor) {
    visitor.visitPyDoubleStarExpression(this);
  }
}
