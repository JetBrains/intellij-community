package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;

/**
 * Implementation does nothing but marking the element type. 
 * User: dcheryasov
 * Date: Jul 6, 2009 1:33:08 AM
 */
public class PyTupleParameterStubImpl extends StubBase<PyTupleParameter>  implements PyTupleParameterStub {
  private final boolean myHasDefaultValue;

  protected PyTupleParameterStubImpl(boolean hasDefaultValue, StubElement parent) {
    super(parent, PyElementTypes.TUPLE_PARAMETER);
    myHasDefaultValue = hasDefaultValue;
  }

  public boolean hasDefaultValue() {
    return myHasDefaultValue;
  }
}
