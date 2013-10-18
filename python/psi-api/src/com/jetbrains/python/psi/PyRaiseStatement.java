package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyRaiseStatement extends PyStatement {
  @NotNull
  PyExpression[] getExpressions();
}
