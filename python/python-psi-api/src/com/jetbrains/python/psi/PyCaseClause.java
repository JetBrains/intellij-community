// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstCaseClause;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyCaseClause extends PyAstCaseClause, PyStatementPart {
  @Override
  default @Nullable PyPattern getPattern() {
    return (PyPattern)PyAstCaseClause.super.getPattern();
  }

  @Override
  default @Nullable PyExpression getGuardCondition() {
    return (PyExpression)PyAstCaseClause.super.getGuardCondition();
  }

  @ApiStatus.Internal
  @Nullable PyType getSubjectTypeAfter(@NotNull TypeEvalContext context);
}
