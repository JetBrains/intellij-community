// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PyParameterListStubImpl extends StubBase<PyParameterList> implements PyParameterListStub {
  public PyParameterListStubImpl(final StubElement parent, IStubElementType stubElementType) {
    super(parent, stubElementType);
  }
}