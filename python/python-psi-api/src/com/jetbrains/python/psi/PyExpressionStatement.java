// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstExpressionStatement;
import org.jetbrains.annotations.NotNull;


public interface PyExpressionStatement extends PyAstExpressionStatement, PyStatement {
  @Override
  default @NotNull PyExpression getExpression() {
    return (PyExpression)PyAstExpressionStatement.super.getExpression();
  }
}
