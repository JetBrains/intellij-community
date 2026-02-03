// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;


import com.jetbrains.python.ast.PyAstWithStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public interface PyWithStatement extends PyAstWithStatement, PyCompoundStatement, PyNamedElementContainer, PyStatementListContainer {
  @Override
  default @NotNull PyStatementList getStatementList() {
    return (PyStatementList)PyAstWithStatement.super.getStatementList();
  }

  @Override
  PyWithItem[] getWithItems();

  boolean isSuppressingExceptions(TypeEvalContext context);
}
