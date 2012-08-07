package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a literal dict expression, e.g. <tt>{'a': 1}</tt>
 */
public interface PyDictLiteralExpression extends PySequenceExpression {
  @NotNull
  PyKeyValueExpression[] getElements();
}
