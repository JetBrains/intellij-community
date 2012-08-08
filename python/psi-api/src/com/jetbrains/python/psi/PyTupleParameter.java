package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.NotNull;

/**
 * Tuple parameter. Defines nothing; this interface is only needed for stub creation.
 */
public interface PyTupleParameter extends PyParameter, StubBasedPsiElement<PyTupleParameterStub> {

  /**
   * @return the nested parameters within this tuple parameter.
   */
  @NotNull
  PyParameter[] getContents();
}
