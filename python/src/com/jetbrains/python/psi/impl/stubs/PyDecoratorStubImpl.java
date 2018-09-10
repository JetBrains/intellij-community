// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;

/**
 * Decorator stub storage.
 * User: dcheryasov
 */
public class PyDecoratorStubImpl extends StubBase<PyDecorator> implements PyDecoratorStub {
  private final QualifiedName myQualifiedName;

  protected PyDecoratorStubImpl(final QualifiedName qualname, final StubElement parent) {
    super(parent, PyElementTypes.DECORATOR_CALL);
    myQualifiedName = qualname;
  }

  @Override
  public QualifiedName getQualifiedName() {
    return myQualifiedName;
  }
}
