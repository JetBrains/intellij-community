// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyStubElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.impl.PyDecoratorListImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;

import java.io.IOException;

@ApiStatus.Internal
public class PyDecoratorListElementType extends PyStubElementFactory<PyDecoratorListStub, PyDecoratorList> {

  @Override
  public PyDecoratorList createPsi(final @NotNull PyDecoratorListStub stub) {
    return new PyDecoratorListImpl(stub);
  }

  @Override
  public @NotNull PyDecoratorListStub createStub(final @NotNull PyDecoratorList psi, final StubElement parentStub) {
    return new PyDecoratorListStubImpl(parentStub);
  }

  @Override
  public void serialize(final @NotNull PyDecoratorListStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
    // nothing
  }

  @Override
  public @NotNull PyDecoratorListStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub)
    throws IOException {
    return new PyDecoratorListStubImpl(parentStub);
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyStubElementTypes.DECORATOR_LIST;
  }
}
