/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyNamedParameter;

public interface PyNamedParameterStub extends NamedStub<PyNamedParameter> {
  boolean isPositionalContainer();
  boolean isKeywordContainer();
  boolean hasDefaultValue();
}