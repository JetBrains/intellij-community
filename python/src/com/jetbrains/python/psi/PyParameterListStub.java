/*
 * @author max
 */
package com.jetbrains.python.psi;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.Stubbed;

public interface PyParameterListStub extends StubElement {
  @Stubbed
  PyParameter[] getParameters();
}