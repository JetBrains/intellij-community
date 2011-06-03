/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyFunctionStub;

public class PyFunctionStubImpl extends StubBase<PyFunction> implements PyFunctionStub {
  private final String myName;
  private final String myReturnTypeFromDocString;

  public PyFunctionStubImpl(final String name, final String returnTypeFromDocString, final StubElement parent) {
    super(parent, PyElementTypes.FUNCTION_DECLARATION);
    myName = name;
    myReturnTypeFromDocString = returnTypeFromDocString;
  }

  public String getName() {
    return myName;
  }

  public String getReturnTypeFromDocString() {
    return myReturnTypeFromDocString;
  }

  @Override
  public String toString() {
    return "PyFunctionStub(" + myName + ")";
  }
}