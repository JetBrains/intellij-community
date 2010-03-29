package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyRaiseStatement extends PyStatement {
  @Nullable
  PyExpression[] getExpressions();
}
