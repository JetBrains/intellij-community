package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyYieldExpression extends PyExpression {
  @Nullable
  PyExpression getExpression();
}
