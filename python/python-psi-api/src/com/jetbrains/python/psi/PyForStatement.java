// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstForStatement;
import org.jetbrains.annotations.NotNull;

/**
 * The 'for/else' statement.
 */
public interface PyForStatement extends PyAstForStatement, PyLoopStatement, PyStatementWithElse, PyNamedElementContainer {
  @Override
  default @NotNull PyForPart getForPart() {
    return (PyForPart)PyAstForStatement.super.getForPart();
  }
}
