// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

/**
 * Represents assignment expressions introduced in Python 3.8 (PEP 572).
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface PyAstAssignmentExpression : PyAstExpression {
  /**
   * @return LHS of an expression (before :=), null if underlying target is not an identifier.
   */
  val target: PyAstTargetExpression? get() = firstChild as? PyAstTargetExpression
  /**
   * @return RHS of an expression (after :=), null if assigned value is omitted or not an expression.
   */
  val assignedValue: PyAstExpression? get() = lastChild as? PyAstExpression
}
