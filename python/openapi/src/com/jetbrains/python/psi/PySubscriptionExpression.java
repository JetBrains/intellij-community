package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySubscriptionExpression extends PyQualifiedExpression, PyReferenceOwner {
  PyExpression getOperand();

  @Nullable
  PyExpression getIndexExpression();
}
