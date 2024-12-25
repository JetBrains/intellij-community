// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstKeyValueExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyKeyValueExpression extends PyAstKeyValueExpression, PyExpression {
  PyKeyValueExpression[] EMPTY_ARRAY = new PyKeyValueExpression[0]; 

  @Override
  default @NotNull PyExpression getKey() {
    return (PyExpression)PyAstKeyValueExpression.super.getKey();
  }

  @Override
  default @Nullable PyExpression getValue() {
    return (PyExpression)PyAstKeyValueExpression.super.getValue();
  }
}
