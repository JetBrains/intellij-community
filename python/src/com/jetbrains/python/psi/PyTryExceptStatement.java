package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The 'try/except/else/finally' statement.
 */
public interface PyTryExceptStatement extends PyStatementWithElse {
  @NotNull
  PyTryPart getTryPart();

  @NotNull
  PyExceptPart[] getExceptParts();

  @Nullable
  PyFinallyPart getFinallyPart();

}
