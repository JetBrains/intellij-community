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

/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyNamedParameterElementType extends PyStubElementType<PyNamedParameterStub, PyNamedParameter> {
  private static final int POSITIONAL_CONTAINER = 1;
  private static final int KEYWORD_CONTAINER = 2;
  private static final int HAS_DEFAULT_VALUE = 4;

  public PyNamedParameterElementType() {
    this("NAMED_PARAMETER");
  }

  public PyNamedParameterElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  @NotNull
  public PyNamedParameter createPsi(@NotNull final PyNamedParameterStub stub) {
    return new PyNamedParameterImpl(stub);
  }

  @Override
  @NotNull
  public PyNamedParameterStub createStub(@NotNull final PyNamedParameter psi, final StubElement parentStub) {
    return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), psi.hasDefaultValue(),
                                        psi.getDefaultValueText(), psi.getTypeCommentAnnotation(), psi.getAnnotationValue() , parentStub,
                                        getStubElementType());
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyNamedParameterImpl(node);
  }

  @Override
  public void serialize(@NotNull final PyNamedParameterStub stub, @NotNull final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());

    byte flags = 0;
    if (stub.isPositionalContainer()) flags |= POSITIONAL_CONTAINER;
    if (stub.isKeywordContainer()) flags |= KEYWORD_CONTAINER;
    if (stub.hasDefaultValue()) flags |= HAS_DEFAULT_VALUE;
    dataStream.writeByte(flags);
    dataStream.writeName(stub.getDefaultValueText());
    dataStream.writeName(stub.getTypeComment());
    dataStream.writeName(stub.getAnnotation());
  }

  @Override
  @NotNull
  public PyNamedParameterStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    final StringRef defaultValueText = dataStream.readName();
    final StringRef typeComment = dataStream.readName();
    final StringRef annotation = dataStream.readName();
    return new PyNamedParameterStubImpl(name,
                                        (flags & POSITIONAL_CONTAINER) != 0,
                                        (flags & KEYWORD_CONTAINER) != 0,
                                        (flags & HAS_DEFAULT_VALUE) != 0,
                                        defaultValueText == null ? null : defaultValueText.getString(),
                                        typeComment == null ? null : typeComment.getString(),
                                        annotation == null ? null : annotation.getString(),
                                        parentStub, getStubElementType());
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    final ASTNode paramList = node.getTreeParent();
    if (paramList != null) {
      final ASTNode container = paramList.getTreeParent();
      if (container != null && container.getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
        return false;
      }
    }
    return super.shouldCreateStub(node);
  }

  @NotNull
  protected IStubElementType getStubElementType() {
    return PyElementTypes.NAMED_PARAMETER;
  }
}