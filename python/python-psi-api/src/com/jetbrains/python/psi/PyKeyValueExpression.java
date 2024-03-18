// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstKeyValueExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyKeyValueExpression extends PyAstKeyValueExpression, PyExpression {
  PyKeyValueExpression[] EMPTY_ARRAY = new PyKeyValueExpression[0]; 

  @Override
  @NotNull
  default PyExpression getKey() {
    return (PyExpression)PyAstKeyValueExpression.super.getKey();
  }

  @Override
  @Nullable
  default PyExpression getValue() {
    return (PyExpression)PyAstKeyValueExpression.super.getValue();
  }
}
