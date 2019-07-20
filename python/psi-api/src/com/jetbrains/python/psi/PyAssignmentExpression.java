// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents assignment expressions introduced in Python 3.8 (PEP 572).
 */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2019.2")
public interface PyAssignmentExpression extends PyExpression {

  /**
   * @return LHS of an expression (before :=)
   */
  @NotNull
  PyTargetExpression getTarget();

  /**
   * @return RHS of an expression (after :=)
   */
  @Nullable
  PyExpression getAssignedValue();
}
