package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.PyDecorator;

/**
 * Decorator stub storage.
 * User: dcheryasov
 * Date: Dec 18, 2008 10:01:57 PM
 */
public class PyDecoratorStubImpl extends StubBase<PyDecorator> implements PyDecoratorStub {
  private String myName;
  private boolean myIsBuiltin;

  protected PyDecoratorStubImpl(final String name, final boolean builtuin, final StubElement parent) {
    super(parent, PyElementTypes.DECORATOR_CALL);
    myName = name;
    myIsBuiltin = builtuin;
  }

  public String getName() {
    return myName;
  }

  public boolean isBuiltin() {
    return myIsBuiltin;  
  }
}
