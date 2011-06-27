package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.Nullable;

public class PyFunctionStubImpl extends StubBase<PyFunction> implements PyFunctionStub {
  private final String myName;
  private final String myReturnTypeFromDocString;
  private final StringRef myDeprecationMessage;

  public PyFunctionStubImpl(final String name, final String returnTypeFromDocString, @Nullable final StringRef deprecationMessage,
                            final StubElement parent) {
    super(parent, PyElementTypes.FUNCTION_DECLARATION);
    myName = name;
    myReturnTypeFromDocString = returnTypeFromDocString;
    myDeprecationMessage = deprecationMessage;
  }

  public String getName() {
    return myName;
  }

  public String getReturnTypeFromDocString() {
    return myReturnTypeFromDocString;
  }

  @Override
  public String getDeprecationMessage() {
    return myDeprecationMessage == null ? null : myDeprecationMessage.getString();
  }

  @Override
  public String toString() {
    return "PyFunctionStub(" + myName + ")";
  }
}