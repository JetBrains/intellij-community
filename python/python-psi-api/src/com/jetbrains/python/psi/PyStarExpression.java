// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstStarExpression;
import org.jetbrains.annotations.Nullable;

public interface PyStarExpression extends PyAstStarExpression, PyExpression {
  @Override
  default @Nullable PyExpression getExpression() {
    return (PyExpression)PyAstStarExpression.super.getExpression();
  }
}
