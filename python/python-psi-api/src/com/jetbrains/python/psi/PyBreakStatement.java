// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstBreakStatement;
import org.jetbrains.annotations.Nullable;


public interface PyBreakStatement extends PyAstBreakStatement, PyStatement {
  @Override
  default @Nullable PyLoopStatement getLoopStatement() {
    return (PyLoopStatement)PyAstBreakStatement.super.getLoopStatement();
  }
}
