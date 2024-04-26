// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstExpressionStatement;
import org.jetbrains.annotations.NotNull;


public interface PyExpressionStatement extends PyAstExpressionStatement, PyStatement {
  @Override
  @NotNull
  default PyExpression getExpression() {
    return (PyExpression)PyAstExpressionStatement.super.getExpression();
  }
}
