package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTupleParameter;

/**
 * Tuple parameter stub, collects nested parameters from stubs.
 */
public interface PyTupleParameterStub extends StubElement<PyTupleParameter> {
  boolean hasDefaultValue();
}
