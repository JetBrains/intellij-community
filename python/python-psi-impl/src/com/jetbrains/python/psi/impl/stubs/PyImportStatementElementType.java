// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Version;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyImportStatementImpl;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


@ApiStatus.Internal
public class PyImportStatementElementType extends PyStubElementType<PyImportStatementStub, PyImportStatement> {
  public PyImportStatementElementType() {
    this("IMPORT_STATEMENT");
  }

  public PyImportStatementElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return new PyImportStatementImpl(node);
  }

  @Override
  public PyImportStatement createPsi(@NotNull PyImportStatementStub stub) {
    return new PyImportStatementImpl(stub);
  }

  @Override
  public @NotNull PyImportStatementStub createStub(@NotNull PyImportStatement psi, StubElement parentStub) {
    final RangeSet<Version> versions = PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi);
    return new PyImportStatementStubImpl(parentStub, getStubElementType(), versions);
  }

  @Override
  public void serialize(@NotNull PyImportStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
  }

  @Override
  public @NotNull PyImportStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);
    return new PyImportStatementStubImpl(parentStub, getStubElementType(), versions);
  }

  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.IMPORT_STATEMENT;
  }
}