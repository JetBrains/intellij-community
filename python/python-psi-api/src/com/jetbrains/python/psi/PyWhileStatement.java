// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstWhileStatement;
import org.jetbrains.annotations.NotNull;

/**
 * The 'while' statement.
 */
public interface PyWhileStatement extends PyAstWhileStatement, PyLoopStatement, PyStatementWithElse {
  @Override
  default @NotNull PyWhilePart getWhilePart() {
    return (PyWhilePart)PyAstWhileStatement.super.getWhilePart();
  }
}
