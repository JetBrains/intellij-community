/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyStarImportElementImpl;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author vlan
 */
public class PyStarImportElementElementType extends PyStubElementType<PyStarImportElementStub, PyStarImportElement> {
  public PyStarImportElementElementType() {
    super("STAR_IMPORT_ELEMENT");
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyStarImportElementImpl(node);
  }

  public PyStarImportElement createPsi(@NotNull final PyStarImportElementStub stub) {
    return new PyStarImportElementImpl(stub);
  }

  public PyStarImportElementStub createStub(@NotNull final PyStarImportElement psi, final StubElement parentStub) {
    return new PyStarImportElementStubImpl(parentStub);
  }

  public void serialize(@NotNull final PyStarImportElementStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  public PyStarImportElementStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PyStarImportElementStubImpl(parentStub);
  }
}
