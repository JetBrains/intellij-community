// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyDecoratorListImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyDecoratorListElementType extends PyStubElementType<PyDecoratorListStub, PyDecoratorList> {

  public PyDecoratorListElementType() {
    super("DECORATOR_LIST");
  }

  @Override
  public @NotNull PsiElement createElement(final @NotNull ASTNode node) {
    return new PyDecoratorListImpl(node);
  }

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
  public @NotNull PyDecoratorListStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PyDecoratorListStubImpl(parentStub);
  }
}
