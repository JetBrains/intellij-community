// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PySlashParameterStubImpl extends PyStubBase<PySlashParameter> implements PySlashParameterStub {

  public PySlashParameterStubImpl(StubElement parent) {
    super(parent, PyElementTypes.SLASH_PARAMETER);
  }
}
