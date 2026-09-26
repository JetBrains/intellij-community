// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstTryExceptStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The 'try/except/else/finally' statement.
 */
public interface PyTryExceptStatement extends PyAstTryExceptStatement, PyCompoundStatement, PyStatementWithElse {
  @Override
  default @NotNull PyTryPart getTryPart() {
    return (PyTryPart)PyAstTryExceptStatement.super.getTryPart();
  }

  @Override
  PyExceptPart @NotNull [] getExceptParts();

  @Override
  default @Nullable PyFinallyPart getFinallyPart() {
    return (PyFinallyPart)PyAstTryExceptStatement.super.getFinallyPart();
  }
}
