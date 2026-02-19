// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAssignmentExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents assignment expressions introduced in Python 3.8 (PEP 572).
 */
@ApiStatus.NonExtendable
public interface PyAssignmentExpression extends PyAstAssignmentExpression, PyExpression {

  /**
   * @return LHS of an expression (before :=), null if underlying target is not an identifier.
   */
  @Override
  default @Nullable PyTargetExpression getTarget() {
    return (PyTargetExpression)PyAstAssignmentExpression.super.getTarget();
  }

  /**
   * @return RHS of an expression (after :=), null if assigned value is omitted or not an expression.
   */
  @Override
  default @Nullable PyExpression getAssignedValue() {
    return (PyExpression)PyAstAssignmentExpression.super.getAssignedValue();
  }
}
