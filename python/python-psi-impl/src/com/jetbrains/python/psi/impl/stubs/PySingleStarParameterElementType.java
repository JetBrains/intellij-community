// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PySingleStarParameterImpl;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public class PySingleStarParameterElementType extends PyStubElementType<PySingleStarParameterStub, PySingleStarParameter> {
  public PySingleStarParameterElementType() {
    super("SINGLE_STAR_PARAMETER");
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return new PySingleStarParameterImpl(node);
  }

  @Override
  public PySingleStarParameter createPsi(@NotNull PySingleStarParameterStub stub) {
    return new PySingleStarParameterImpl(stub);
  }

  @Override
  public @NotNull PySingleStarParameterStub createStub(@NotNull PySingleStarParameter psi, StubElement parentStub) {
    return new PySingleStarParameterStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PySingleStarParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull PySingleStarParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PySingleStarParameterStubImpl(parentStub);
  }
}
