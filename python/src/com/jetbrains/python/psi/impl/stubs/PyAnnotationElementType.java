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

/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyAnnotationImpl;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyAnnotationElementType extends PyStubElementType<PyAnnotationStub, PyAnnotation> {
  public PyAnnotationElementType() {
    this("ANNOTATION");
  }

  public PyAnnotationElementType(String debugName) {
    super(debugName);
  }

  public PyAnnotation createPsi(@NotNull final PyAnnotationStub stub) {
    return new PyAnnotationImpl(stub);
  }

  public PyAnnotationStub createStub(@NotNull final PyAnnotation psi, final StubElement parentStub) {
    return new PyAnnotationStubImpl(parentStub, PyElementTypes.ANNOTATION);
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyAnnotationImpl(node);
  }

  public void serialize(@NotNull final PyAnnotationStub stub, @NotNull final StubOutputStream dataStream)
      throws IOException {
  }

  @NotNull
  public PyAnnotationStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PyAnnotationStubImpl(parentStub, PyElementTypes.ANNOTATION);
  }
}