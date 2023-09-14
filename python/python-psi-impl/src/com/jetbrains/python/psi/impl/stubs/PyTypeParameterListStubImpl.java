package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;

public class PyTypeParameterListStubImpl extends StubBase<PyTypeParameterList> implements PyTypeParameterListStub {
  public PyTypeParameterListStubImpl(StubElement parent, IStubElementType stubElementType) {
    super(parent, stubElementType);
  }
}
