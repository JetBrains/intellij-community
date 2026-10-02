// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@ApiStatus.Internal
public final class PyNamedTupleStubType extends CustomTargetExpressionStubType<PyNamedTupleStub> {

  @Override
  public @Nullable PyNamedTupleStub createStub(@NotNull PyTargetExpression psi) {
    return PyNamedTupleStubImpl.create(psi);
  }

  @Override
  public @Nullable PyNamedTupleStub deserializeStub(@NotNull StubInputStream stream) throws IOException {
    return PyNamedTupleStubImpl.deserialize(stream);
  }
}
