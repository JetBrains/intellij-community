// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstMatchStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyMatchStatement extends PyAstMatchStatement, PyCompoundStatement {
  @Override
  @Nullable
  default PyExpression getSubject() {
    return (PyExpression)PyAstMatchStatement.super.getSubject();
  }

  @Override
  @NotNull
  default List<PyCaseClause> getCaseClauses() {
    //noinspection unchecked
    return (List<PyCaseClause>)PyAstMatchStatement.super.getCaseClauses();
  }
}
