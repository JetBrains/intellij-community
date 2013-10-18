package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Sep 28, 2008
 */
public class PyDecoratorListStubImpl extends StubBase<PyDecoratorList> implements PyDecoratorListStub {
  public PyDecoratorListStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.DECORATOR_LIST);
  }
}
