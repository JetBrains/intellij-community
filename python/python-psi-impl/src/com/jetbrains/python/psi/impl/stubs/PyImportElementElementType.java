// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyImportElementImpl;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


@ApiStatus.Internal
public class PyImportElementElementType extends PyStubElementFactory<PyImportElementStub, PyImportElement> {
  @Override
  public PyImportElement createPsi(@NotNull PyImportElementStub stub) {
    return new PyImportElementImpl(stub);
  }

  @Override
  public @NotNull PyImportElementStub createStub(@NotNull PyImportElement psi, StubElement parentStub) {
    final PyTargetExpression asName = psi.getAsNameElement();
    return new PyImportElementStubImpl(psi.getImportedQName(), asName != null ? asName.getName() : "", parentStub, getStubElementType());
  }

  @Override
  public void serialize(@NotNull PyImportElementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    QualifiedName.serialize(stub.getImportedQName(), dataStream);
    dataStream.writeName(stub.getAsName());
  }

  @Override
  public @NotNull PyImportElementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    QualifiedName qName = QualifiedName.deserialize(dataStream);
    String asName = dataStream.readNameString();
    return new PyImportElementStubImpl(qName, asName, parentStub, getStubElementType());
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyElementTypes.IMPORT_ELEMENT;
  }
}
