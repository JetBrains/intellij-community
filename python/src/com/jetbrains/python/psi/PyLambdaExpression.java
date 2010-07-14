package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyLambdaExpression extends PyExpression, Callable {
  @Nullable
  public PyExpression getBody();
}
