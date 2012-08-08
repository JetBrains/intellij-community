package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyReturnStatement extends PyStatement {
  @Nullable PyExpression getExpression();
}
