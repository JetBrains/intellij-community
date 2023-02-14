/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyExceptPartImpl;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public class PyExceptPartElementType extends PyStubElementType<PyExceptPartStub, PyExceptPart> {
  public PyExceptPartElementType() {
    super("EXCEPT_PART");
  }

  @NotNull
  @Override
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyExceptPartImpl(node);
  }

  @Override
  public PyExceptPart createPsi(@NotNull PyExceptPartStub stub) {
    return new PyExceptPartImpl(stub);
  }

  @NotNull
  @Override
  public PyExceptPartStub createStub(@NotNull PyExceptPart psi, StubElement parentStub) {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PyExceptPartStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public PyExceptPartStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyExceptPartStubImpl(parentStub);
  }
}
