// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PyDecoratorListStubImpl extends StubBase<PyDecoratorList> implements PyDecoratorListStub {
  public PyDecoratorListStubImpl(final StubElement parent) {
    super(parent, PyStubElementTypes.DECORATOR_LIST);
  }
}
