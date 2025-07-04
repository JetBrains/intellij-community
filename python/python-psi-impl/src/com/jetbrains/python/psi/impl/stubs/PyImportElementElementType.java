// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyImportElementImpl;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public class PyImportElementElementType extends PyStubElementType<PyImportElementStub, PyImportElement> {
  public PyImportElementElementType() {
    this("IMPORT_ELEMENT");
  }

  public PyImportElementElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return new PyImportElementImpl(node);
  }

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

  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.IMPORT_ELEMENT;
  }
}
