// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jetbrains.annotations.Nullable;

/**
 * Decorator stub storage.
 */
public class PyDecoratorStubImpl extends StubBase<PyDecorator> implements PyDecoratorStub {
  private final QualifiedName myQualifiedName;
  private final boolean myHasArgumentList;
  private final @Nullable PyCustomDecoratorStub myCustomStub;

  protected PyDecoratorStubImpl(final QualifiedName qualname,
                                final boolean hasArgumentList,
                                final StubElement parent,
                                final @Nullable PyCustomDecoratorStub customStub) {
    super(parent, PyStubElementTypes.DECORATOR_CALL);
    myQualifiedName = qualname;
    myHasArgumentList = hasArgumentList;
    myCustomStub = customStub;
  }

  @Override
  public QualifiedName getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public boolean hasArgumentList() {
    return myHasArgumentList;
  }

  @Override
  public <T> @Nullable T getCustomStub(Class<T> stubClass) {
    return ObjectUtils.tryCast(myCustomStub, stubClass);
  }

  @Override
  public String toString() {
    return "PyDecoratorStubImpl{" +
           "myQualifiedName=" + myQualifiedName +
           ", myHasArgumentList=" + myHasArgumentList +
           ", myCustomStub=" + myCustomStub +
           '}';
  }
}
