/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.stubs.PyParameterListStub;

public class PyParameterListStubImpl extends StubBase<PyParameterList> implements PyParameterListStub {
  public PyParameterListStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.PARAMETER_LIST);
  }

}