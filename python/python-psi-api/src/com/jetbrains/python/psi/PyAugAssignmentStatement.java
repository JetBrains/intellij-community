// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAugAssignmentStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyAugAssignmentStatement extends PyAstAugAssignmentStatement, PyStatement {
  @Override
  @NotNull
  default PyExpression getTarget() {
    return (PyExpression)PyAstAugAssignmentStatement.super.getTarget();
  }

  @Override
  @Nullable
  default PyExpression getValue() {
    return (PyExpression)PyAstAugAssignmentStatement.super.getValue();
  }
}
