// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public class PyExceptPartStubImpl extends PyStubBase<PyExceptPart> implements PyExceptPartStub {
  protected PyExceptPartStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.EXCEPT_PART);
  }
}
