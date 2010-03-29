package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * The 'while' statement.
 */
public interface PyWhileStatement extends PyLoopStatement, PyStatementWithElse {
  @NotNull PyWhilePart getWhilePart();
}
