/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import com.jetbrains.python.psi.stubs.PyParameterStub;

import java.util.List;

public class PyParameterListStubImpl extends StubBase<PyParameterList> implements PyParameterListStub {
  public PyParameterListStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.PARAMETER_LIST);
  }

  public PyParameterStub[] getParameters() {
    final List<StubElement> stubs = getChildrenStubs();
    PyParameterStub[] result = new PyParameterStub[stubs.size()];

    for (int i = 0; i < stubs.size(); i++) {
      StubElement stub = stubs.get(i);
      assert stub.getStubType() == PyElementTypes.FORMAL_PARAMETER;
      result[i] = (PyParameterStub)stub;
    }

    return result;
  }
}