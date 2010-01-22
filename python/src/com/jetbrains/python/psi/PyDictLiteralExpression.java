package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a literal dict expression, e.g. <tt>{'a': 1}</tt>
 */
public interface PyDictLiteralExpression extends PyExpression {
  @Nullable
  PyKeyValueExpression[] getElements();
}
