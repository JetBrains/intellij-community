package com.jetbrains.python.psi;

/**
 * Describes a generalized expression, possibly typed.
 *
 * @author yole
 */
public interface PyExpression extends PyTypedElement {
  PyExpression[] EMPTY_ARRAY = new PyExpression[0];
}
