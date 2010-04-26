package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a generalized expression, possibly typed.
 *
 * @author yole
 */
public interface PyExpression extends PyElement {
  PyExpression[] EMPTY_ARRAY = new PyExpression[0];

  @Nullable
  PyType getType(@NotNull TypeEvalContext context);
}
