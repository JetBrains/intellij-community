// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.ast.PyAstAssignmentExpression
import org.jetbrains.annotations.ApiStatus

/**
 * Represents assignment expressions introduced in Python 3.8 (PEP 572).
 */
@ApiStatus.NonExtendable
interface PyAssignmentExpression : PyAstAssignmentExpression, PyExpression {
  override val target: PyTargetExpression?
    /**
     * @return LHS of an expression (before :=), null if underlying target is not an identifier.
     */
    get() = super<PyAstAssignmentExpression>.target as PyTargetExpression?

  override val assignedValue: PyExpression?
    /**
     * @return RHS of an expression (after :=), null if assigned value is omitted or not an expression.
     */
    get() = super<PyAstAssignmentExpression>.assignedValue as PyExpression?
}
