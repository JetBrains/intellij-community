package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyDelStatement extends PyStatement {
  @Nullable
  PyExpression[] getTargets();
}
