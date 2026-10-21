// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyStubElementTypes;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.impl.PySlashParameterImpl;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;

@ApiStatus.Internal
public class PySlashParameterElementType extends PyStubElementFactory<PySlashParameterStub, PySlashParameter> {

  @Override
  public PySlashParameter createPsi(@NotNull PySlashParameterStub stub) {
    return new PySlashParameterImpl(stub);
  }

  @Override
  public @NotNull PySlashParameterStub createStub(@NotNull PySlashParameter psi, StubElement parentStub) {
    return new PySlashParameterStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PySlashParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull PySlashParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) {
    return new PySlashParameterStubImpl(parentStub);
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyStubElementTypes.SLASH_PARAMETER;
  }
}
