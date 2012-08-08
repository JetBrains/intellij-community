package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyNonlocalStatement extends PyStatement {
  @NotNull
  PyTargetExpression[] getVariables();
}
