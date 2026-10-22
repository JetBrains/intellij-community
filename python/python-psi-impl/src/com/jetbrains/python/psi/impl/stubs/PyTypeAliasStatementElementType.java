// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.openapi.util.Version;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyTypeAliasStatementImpl;
import com.jetbrains.python.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyTypeAliasNameIndex;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public class PyTypeAliasStatementElementType extends PyStubElementFactory<PyTypeAliasStatementStub, PyTypeAliasStatement> {

  @Override
  public PyTypeAliasStatement createPsi(@NotNull PyTypeAliasStatementStub stub) {
    return new PyTypeAliasStatementImpl(stub);
  }

  @Override
  public @NotNull PyTypeAliasStatementStub createStub(@NotNull PyTypeAliasStatement psi, StubElement<? extends PsiElement> parentStub) {
    return new PyTypeAliasStatementStubImpl(psi.getName(), (psi.getTypeExpression() != null ? psi.getTypeExpression().getText() : null),
                                            parentStub, getStubElementType(), PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi));
  }

  @Override
  public void indexStub(@NotNull PyTypeAliasStatementStub stub, @NotNull IndexSink sink) {
    String name = stub.getName();
    if (name != null && PyUtil.getInitialUnderscores(name) == 0 && stub.getParentStub() instanceof PyFileStub) {
      sink.occurrence(PyTypeAliasNameIndex.KEY, name);
      sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
    }
  }

  @Override
  public void serialize(@NotNull PyTypeAliasStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getTypeExpressionText());
    PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
  }

  @Override
  public @NotNull PyTypeAliasStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    String typeExpressionText = dataStream.readNameString();
    RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);

    return new PyTypeAliasStatementStubImpl(name, typeExpressionText, parentStub, getStubElementType(), versions);
  }

  @Override
  protected @NotNull IElementType getStubElementType() {
    return PyElementTypes.TYPE_ALIAS_STATEMENT;
  }
}
