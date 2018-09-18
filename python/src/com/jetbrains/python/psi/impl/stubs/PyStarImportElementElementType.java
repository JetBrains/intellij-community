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

/**
 * @author vlan
 */
public class PyStarImportElementElementType extends PyStubElementType<PyStarImportElementStub, PyStarImportElement> {
  public PyStarImportElementElementType() {
    super("STAR_IMPORT_ELEMENT");
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyStarImportElementImpl(node);
  }

  @Override
  public PyStarImportElement createPsi(@NotNull final PyStarImportElementStub stub) {
    return new PyStarImportElementImpl(stub);
  }

  @Override
  @NotNull
  public PyStarImportElementStub createStub(@NotNull final PyStarImportElement psi, final StubElement parentStub) {
    return new PyStarImportElementStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull final PyStarImportElementStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
  }

  @Override
  @NotNull
  public PyStarImportElementStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PyStarImportElementStubImpl(parentStub);
  }
}
