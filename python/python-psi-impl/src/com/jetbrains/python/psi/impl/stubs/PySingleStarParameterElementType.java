// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.impl.PySingleStarParameterImpl;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;

import java.io.IOException;


@ApiStatus.Internal
public class PySingleStarParameterElementType extends PyStubElementFactory<PySingleStarParameterStub, PySingleStarParameter> {
  @Override
  public PySingleStarParameter createPsi(@NotNull PySingleStarParameterStub stub) {
    return new PySingleStarParameterImpl(stub);
  }

  @Override
  public @NotNull PySingleStarParameterStub createStub(@NotNull PySingleStarParameter psi, StubElement parentStub) {
    return new PySingleStarParameterStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PySingleStarParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull PySingleStarParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PySingleStarParameterStubImpl(parentStub);
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyElementTypes.SINGLE_STAR_PARAMETER;
  }
}
