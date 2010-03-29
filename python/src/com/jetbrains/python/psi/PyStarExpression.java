package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author: Alexey.Ivanov
 */
public interface PyStarExpression extends PyExpression {
  @Nullable
  PyExpression getExpression();
}
