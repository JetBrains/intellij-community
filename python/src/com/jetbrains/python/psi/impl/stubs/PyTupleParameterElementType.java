/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.impl.PyTupleParameterImpl;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Does actual storing and loading of tuple parameter stub. Not much to do.
 */
public class PyTupleParameterElementType extends PyStubElementType<PyTupleParameterStub, PyTupleParameter> {

  public PyTupleParameterElementType() {
    super("TUPLE_PARAMETER");
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyTupleParameterImpl(node);
  }

  @Override
  public PyTupleParameter createPsi(@NotNull PyTupleParameterStub stub) {
    return new PyTupleParameterImpl(stub);
  }

  @Override
  @NotNull
  public PyTupleParameterStub createStub(@NotNull PyTupleParameter psi, StubElement parentStub) {
    return new PyTupleParameterStubImpl(psi.hasDefaultValue(), psi.getDefaultValueText(), parentStub);
  }

  @Override
  @NotNull
  public PyTupleParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    final boolean hasDefaultValue = dataStream.readBoolean();
    final StringRef defaultValueText = dataStream.readName();
    return new PyTupleParameterStubImpl(hasDefaultValue, defaultValueText == null ? null : defaultValueText.getString(), parentStub);
  }

  @Override
  public void serialize(@NotNull PyTupleParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.hasDefaultValue());
    dataStream.writeName(stub.getDefaultValueText());
  }
}
