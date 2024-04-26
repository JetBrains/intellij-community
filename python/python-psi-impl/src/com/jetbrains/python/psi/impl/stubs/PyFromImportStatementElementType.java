// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFromImportStatementImpl;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public class PyFromImportStatementElementType extends PyStubElementType<PyFromImportStatementStub, PyFromImportStatement> {
  public PyFromImportStatementElementType() {
    this("FROM_IMPORT_STATEMENT");
  }

  public PyFromImportStatementElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @NotNull
  @Override
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyFromImportStatementImpl(node);
  }

  @Override
  public PyFromImportStatement createPsi(@NotNull PyFromImportStatementStub stub) {
    return new PyFromImportStatementImpl(stub);
  }

  @NotNull
  @Override
  public PyFromImportStatementStub createStub(@NotNull PyFromImportStatement psi, StubElement parentStub) {
    return new PyFromImportStatementStubImpl(psi.getImportSourceQName(), psi.isStarImport(), psi.getRelativeLevel(), parentStub,
                                             getStubElementType());
  }

  @Override
  public void serialize(@NotNull PyFromImportStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    final QualifiedName qName = stub.getImportSourceQName();
    QualifiedName.serialize(qName, dataStream);
    dataStream.writeBoolean(stub.isStarImport());
    dataStream.writeVarInt(stub.getRelativeLevel());
  }

  @Override
  @NotNull
  public PyFromImportStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    QualifiedName qName = QualifiedName.deserialize(dataStream);
    boolean isStarImport = dataStream.readBoolean();
    int relativeLevel = dataStream.readVarInt();
    return new PyFromImportStatementStubImpl(qName, isStarImport, relativeLevel, parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.FROM_IMPORT_STATEMENT;
  }
}
