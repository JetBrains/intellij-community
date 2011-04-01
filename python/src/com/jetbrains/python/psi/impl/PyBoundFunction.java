package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyBoundFunction extends PyFunctionImpl {
  public PyBoundFunction(PyFunction function) {
    super(function.getNode());
  }
}
