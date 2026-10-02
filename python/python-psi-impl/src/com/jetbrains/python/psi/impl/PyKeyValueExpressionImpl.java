// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


@ApiStatus.Internal
public class PyKeyValueExpressionImpl extends PyElementImpl implements PyKeyValueExpression {
  public PyKeyValueExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeyValueExpression(this);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType keyType = context.getType(getKey());
    final PyExpression value = getValue();
    PyType valueType = PyAnyType.getUnknown();
    if (value != null) {
      valueType = context.getType(value);
    }
    final PyType tupleType = PyTupleType.create(this, Arrays.asList(keyType, valueType));
    return tupleType != null ? tupleType : PyAnyType.getUnknown();
  }
}
