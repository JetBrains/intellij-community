package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyDelStatement extends PyStatement {
  @NotNull
  PyExpression[] getTargets();
}
