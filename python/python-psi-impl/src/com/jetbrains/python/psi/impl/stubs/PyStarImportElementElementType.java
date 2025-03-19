// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyStarImportElementImpl;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyStarImportElementElementType extends PyStubElementType<PyStarImportElementStub, PyStarImportElement> {
  public PyStarImportElementElementType() {
    super("STAR_IMPORT_ELEMENT");
  }

  @Override
  public @NotNull PsiElement createElement(final @NotNull ASTNode node) {
    return new PyStarImportElementImpl(node);
  }

  @Override
  public PyStarImportElement createPsi(final @NotNull PyStarImportElementStub stub) {
    return new PyStarImportElementImpl(stub);
  }

  @Override
  public @NotNull PyStarImportElementStub createStub(final @NotNull PyStarImportElement psi, final StubElement parentStub) {
    return new PyStarImportElementStubImpl(parentStub);
  }

  @Override
  public void serialize(final @NotNull PyStarImportElementStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull PyStarImportElementStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PyStarImportElementStubImpl(parentStub);
  }
}
