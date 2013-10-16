package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.PyDecorator;

/**
 * Decorator stub storage.
 * User: dcheryasov
 * Date: Dec 18, 2008 10:01:57 PM
 */
public class PyDecoratorStubImpl extends StubBase<PyDecorator> implements PyDecoratorStub {
  private final QualifiedName myQualifiedName;

  protected PyDecoratorStubImpl(final QualifiedName qualname, final StubElement parent) {
    super(parent, PyElementTypes.DECORATOR_CALL);
    myQualifiedName = qualname;
  }

  public QualifiedName getQualifiedName() {
    return myQualifiedName;
  }
}
