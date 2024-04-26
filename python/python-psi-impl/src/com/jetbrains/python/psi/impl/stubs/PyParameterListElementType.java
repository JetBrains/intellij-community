// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyParameterListImpl;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyParameterListElementType extends PyStubElementType<PyParameterListStub, PyParameterList> {
  public PyParameterListElementType() {
    this("PARAMETER_LIST");
  }

  public PyParameterListElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  public PyParameterList createPsi(@NotNull final PyParameterListStub stub) {
    return new PyParameterListImpl(stub);
  }

  @Override
  @NotNull
  public PyParameterListStub createStub(@NotNull final PyParameterList psi, final StubElement parentStub) {
    return new PyParameterListStubImpl(parentStub, getStubElementType());
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyParameterListImpl(node);
  }

  @Override
  public void serialize(@NotNull final PyParameterListStub stub, @NotNull final StubOutputStream dataStream)
      throws IOException {
  }

  @Override
  @NotNull
  public PyParameterListStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PyParameterListStubImpl(parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.PARAMETER_LIST;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    if (node.getTreeParent().getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
      return false;
    }
    return super.shouldCreateStub(node);
  }
}