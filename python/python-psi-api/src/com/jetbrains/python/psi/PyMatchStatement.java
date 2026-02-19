// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstMatchStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyMatchStatement extends PyAstMatchStatement, PyCompoundStatement {
  @Override
  default @Nullable PyExpression getSubject() {
    return (PyExpression)PyAstMatchStatement.super.getSubject();
  }

  @Override
  default @NotNull List<PyCaseClause> getCaseClauses() {
    //noinspection unchecked
    return (List<PyCaseClause>)PyAstMatchStatement.super.getCaseClauses();
  }
}
