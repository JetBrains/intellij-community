package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyTupleExpression extends PyExpression, Iterable<PyExpression> {
  @NotNull PyExpression[] getElements();
}
