package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

public interface PyParenthesizedExpression extends PyExpression {
  @Nullable
  PyExpression getContainedExpression();
}
