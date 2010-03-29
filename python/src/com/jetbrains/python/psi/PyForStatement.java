package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * The 'for/else' statement.
 */
public interface PyForStatement extends PyLoopStatement, PyStatementWithElse, NameDefiner {
  @NotNull PyForPart getForPart();
}
