package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyPrefixExpression extends PyQualifiedExpression, PyReferenceOwner {
  @Nullable
  PyExpression getOperand();
  PyElementType getOperator();
}
