package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PyAssertStatement extends PyStatement {
  PyExpression[] getArguments();
}
