package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyPrefixExpression extends PyExpression {
  @Nullable
  PyExpression getOperand();
  PyElementType getOperationSign();
}
