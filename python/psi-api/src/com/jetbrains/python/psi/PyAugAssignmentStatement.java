package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyAugAssignmentStatement extends PyStatement {
  @NotNull
  PyExpression getTarget();
  @Nullable
  PyExpression getValue();
}
