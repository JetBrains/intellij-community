package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * The 'if/elif/else' statement.
 */
public interface PyIfStatement extends PyStatementWithElse {
  @NotNull PyIfPart getIfPart();
  @NotNull PyIfPart[] getElifParts();
}
