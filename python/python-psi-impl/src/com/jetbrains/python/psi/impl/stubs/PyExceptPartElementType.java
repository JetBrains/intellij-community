// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyStubElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.impl.PyExceptPartImpl;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

import java.io.IOException;


@ApiStatus.Internal
public class PyExceptPartElementType extends PyStubElementFactory<PyExceptPartStub, PyExceptPart> {
  @Override
  public PyExceptPart createPsi(@NotNull PyExceptPartStub stub) {
    return new PyExceptPartImpl(stub);
  }

  @Override
  public @NotNull PyExceptPartStub createStub(@NotNull PyExceptPart psi, StubElement parentStub) {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PyExceptPartStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull PyExceptPartStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyStubElementTypes.EXCEPT_PART;
  }
}
