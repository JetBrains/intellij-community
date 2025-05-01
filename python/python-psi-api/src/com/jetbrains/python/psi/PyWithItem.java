// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstWithItem;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyWithItem extends PyAstWithItem, PyElement {
  PyWithItem[] EMPTY_ARRAY = new PyWithItem[0];

  @Override
  default @NotNull PyExpression getExpression() {
    return (PyExpression)PyAstWithItem.super.getExpression();
  }

  @Override
  default @Nullable PyExpression getTarget() {
    return (PyExpression)PyAstWithItem.super.getTarget();
  }

  boolean isSuppressingExceptions(TypeEvalContext context);
}
