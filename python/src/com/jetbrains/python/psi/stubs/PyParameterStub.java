/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyParameter;

public interface PyParameterStub extends NamedStub<PyParameter> {
  boolean isPositionalContainer();
  boolean isKeywordContainer();
}