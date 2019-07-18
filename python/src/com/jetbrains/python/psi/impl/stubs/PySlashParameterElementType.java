// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PySlashParameterImpl;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
@ApiStatus.AvailableSince("2019.2")
public class PySlashParameterElementType extends PyStubElementType<PySlashParameterStub, PySlashParameter> {

  public PySlashParameterElementType() {
    super("SLASH_PARAMETER");
  }

  @NotNull
  @Override
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PySlashParameterImpl(node);
  }

  @Override
  public PySlashParameter createPsi(@NotNull PySlashParameterStub stub) {
    return new PySlashParameterImpl(stub);
  }

  @NotNull
  @Override
  public PySlashParameterStub createStub(@NotNull PySlashParameter psi, StubElement parentStub) {
    return new PySlashParameterStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PySlashParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  @NotNull
  public PySlashParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) {
    return new PySlashParameterStubImpl(parentStub);
  }
}
