// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstCaseClause;
import org.jetbrains.annotations.Nullable;

public interface PyCaseClause extends PyAstCaseClause, PyStatementPart, PyCaptureContext {
  @Override
  default @Nullable PyPattern getPattern() {
    return (PyPattern)PyAstCaseClause.super.getPattern();
  }

  @Override
  default @Nullable PyExpression getGuardCondition() {
    return (PyExpression)PyAstCaseClause.super.getGuardCondition();
  }
}
