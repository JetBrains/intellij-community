package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a qualified expression, that is, of "a.b.c..." sort.
 * User: dcheryasov
 * Date: Oct 18, 2008
 */
public interface PyQualifiedExpression extends PyExpression {
  @Nullable
  PyExpression getQualifier();
}
