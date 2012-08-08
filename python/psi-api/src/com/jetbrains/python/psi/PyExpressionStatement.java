package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyExpressionStatement extends PyStatement {
  @NotNull
  PyExpression getExpression();
}
