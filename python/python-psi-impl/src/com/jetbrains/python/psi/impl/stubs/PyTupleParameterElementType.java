// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyStubElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.impl.PyTupleParameterImpl;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;

import java.io.IOException;

/**
 * Does actual storing and loading of tuple parameter stub. Not much to do.
 */
@ApiStatus.Internal
public class PyTupleParameterElementType extends PyStubElementFactory<PyTupleParameterStub, PyTupleParameter> {

  @Override
  public PyTupleParameter createPsi(@NotNull PyTupleParameterStub stub) {
    return new PyTupleParameterImpl(stub);
  }

  @Override
  public @NotNull PyTupleParameterStub createStub(@NotNull PyTupleParameter psi, StubElement parentStub) {
    return new PyTupleParameterStubImpl(psi.getDefaultValueText(), parentStub);
  }

  @Override
  public @NotNull PyTupleParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyTupleParameterStubImpl(dataStream.readNameString(), parentStub);
  }

  @Override
  public void serialize(@NotNull PyTupleParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getDefaultValueText());
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyStubElementTypes.TUPLE_PARAMETER;
  }
}
