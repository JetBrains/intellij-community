package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PyNonlocalStatement extends PyStatement {
  void accept(PyElementVisitor visitor);
}
