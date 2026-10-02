// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.impl.PyTypeParameterListImpl;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public class PyTypeParameterListElementType extends PyStubElementType<PyTypeParameterListStub, PyTypeParameterList> {

  public PyTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST");
  }

  @Override
  public PyTypeParameterList createPsi(@NotNull PyTypeParameterListStub stub) {
    return new PyTypeParameterListImpl(stub);
  }

  @Override
  public @NotNull PyTypeParameterListStub createStub(@NotNull PyTypeParameterList psi, StubElement<? extends PsiElement> parentStub) {
    return new PyTypeParameterListStubImpl(parentStub, getStubElementType());
  }

  @Override
  public void serialize(@NotNull PyTypeParameterListStub stub, @NotNull StubOutputStream dataStream) throws IOException {

  }

  @Override
  public @NotNull PyTypeParameterListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyTypeParameterListStubImpl(parentStub, getStubElementType());
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return new PyTypeParameterListImpl(node);
  }

  protected IStubElementType<PyTypeParameterListStub, PyTypeParameterList> getStubElementType() {
    return PyStubElementTypes.TYPE_PARAMETER_LIST;
  }
}
