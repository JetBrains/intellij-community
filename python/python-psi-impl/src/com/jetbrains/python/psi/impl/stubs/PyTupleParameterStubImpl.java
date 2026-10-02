// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation does nothing but marking the element type.
 */
@ApiStatus.Internal
public class PyTupleParameterStubImpl extends StubBase<PyTupleParameter> implements PyTupleParameterStub {
  private final @Nullable String myDefaultValueText;

  protected PyTupleParameterStubImpl(@Nullable String defaultValueText, StubElement parent) {
    super(parent, PyStubElementTypes.TUPLE_PARAMETER);
    myDefaultValueText = defaultValueText;
  }

  @Override
  public @Nullable String getDefaultValueText() {
    return myDefaultValueText;
  }

  @Override
  public String toString() {
    return "PyTupleParameterStubImpl{" +
           "myDefaultValueText='" + myDefaultValueText + '\'' +
           '}';
  }
}
