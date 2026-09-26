// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstStatementWithElse;
import org.jetbrains.annotations.Nullable;

/**
 * A part of a multi-part statement which can have an "else:" clause.
 */
public interface PyStatementWithElse extends PyAstStatementWithElse, PyStatement {
  @Override
  default @Nullable PyElsePart getElsePart() {
    return (PyElsePart)PyAstStatementWithElse.super.getElsePart();
  }
}
