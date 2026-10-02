// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public class PyAnnotationStubImpl extends StubBase<PyAnnotation> implements PyAnnotationStub {
  protected PyAnnotationStubImpl(final StubElement parent, final IStubElementType elementType) {
    super(parent, elementType);
  }
}
