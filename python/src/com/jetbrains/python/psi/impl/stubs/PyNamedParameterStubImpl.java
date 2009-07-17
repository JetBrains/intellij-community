/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

public class PyNamedParameterStubImpl extends StubBase<PyNamedParameter> implements PyNamedParameterStub {
  private final String myName;
  private final boolean myPositionalContainer;
  private final boolean myKeywordContainer;

  public PyNamedParameterStubImpl(String name, boolean isPositionalContainer, boolean isKeywordContainer, StubElement parent) {
    super(parent, PyElementTypes.NAMED_PARAMETER);

    myName = name;
    myPositionalContainer = isPositionalContainer;
    myKeywordContainer = isKeywordContainer;
  }

  public boolean isPositionalContainer() {
    return myPositionalContainer;
  }

  public boolean isKeywordContainer() {
    return myKeywordContainer;
  }

  public String getName() {
    return myName;
  }
}