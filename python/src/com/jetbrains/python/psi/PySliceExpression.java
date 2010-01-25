package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PySliceExpression extends PyExpression {
  PyExpression getOperand();
}
