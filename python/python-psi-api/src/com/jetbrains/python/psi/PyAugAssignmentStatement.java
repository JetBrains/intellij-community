// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAugAssignmentStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyAugAssignmentStatement extends PyAstAugAssignmentStatement, PyStatement {
  @Override
  default @NotNull PyExpression getTarget() {
    return (PyExpression)PyAstAugAssignmentStatement.super.getTarget();
  }

  @Override
  default @Nullable PyExpression getValue() {
    return (PyExpression)PyAstAugAssignmentStatement.super.getValue();
  }
}
