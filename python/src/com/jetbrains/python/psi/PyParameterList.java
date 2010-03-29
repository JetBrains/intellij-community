package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyParameterListStub;

/**
 * Represents function parameter list.
 * Date: 29.05.2005
 */
public interface PyParameterList extends PyElement, StubBasedPsiElement<PyParameterListStub>, NameDefiner {

  /**
   * Extracts the individual parameters.
   * Note that tuple parameters are flattened by this method.
   * @return a possibly empty array of named paramaters.
   */
  PyParameter[] getParameters();

  /**
   * Adds a paramter to list, after all other parameters.
   * @param param what to add
   */
  void addParameter(PyNamedParameter param);

}
