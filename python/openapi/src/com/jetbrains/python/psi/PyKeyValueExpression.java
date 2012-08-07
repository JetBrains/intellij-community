package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyKeyValueExpression extends PyExpression {
  PyKeyValueExpression[] EMPTY_ARRAY = new PyKeyValueExpression[0]; 

  @NotNull
  PyExpression getKey();

  @Nullable
  PyExpression getValue();
}
