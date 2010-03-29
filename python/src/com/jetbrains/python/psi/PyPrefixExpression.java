package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PyPrefixExpression extends PyExpression {
  PyExpression getOperand();
  PyElementType getOperationSign();
}
