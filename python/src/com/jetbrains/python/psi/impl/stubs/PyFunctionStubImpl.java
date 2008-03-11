/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.NotNull;

public class PyFunctionStubImpl extends StubBase<PyFunction> implements PyFunctionStub {
  private final String myName;

  public PyFunctionStubImpl(final String name, final StubElement parent) {
    super(parent);
    myName = name;
  }

  public IStubElementType getStubType() {
    return PyElementTypes.FUNCTION_DECLARATION;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public PyParameterListStub getParameterList() {
    for (StubElement childStub : getChildrenStubs()) {
      if (childStub.getStubType() == PyElementTypes.PARAMETER_LIST) {
        return (PyParameterListStub)childStub;
      }
    }

    throw new IllegalStateException("Can't find parameter list for the function");
  }
}