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
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
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

  public PyNamedParameterElementType() {
    this("NAMED_PARAMETER");
  }

  public PyNamedParameterElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull PyNamedParameter createPsi(final @NotNull PyNamedParameterStub stub) {
    return new PyNamedParameterImpl(stub);
  }

  @Override
  public @NotNull PyNamedParameterStub createStub(final @NotNull PyNamedParameter psi, final StubElement parentStub) {
    return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), psi.getDefaultValueText(),
                                        psi.getTypeCommentAnnotation(), psi.getAnnotationValue(), parentStub, getStubElementType());
  }

  @Override
  public @NotNull PsiElement createElement(final @NotNull ASTNode node) {
    return new PyNamedParameterImpl(node);
  }

  @Override
  public void serialize(final @NotNull PyNamedParameterStub stub, final @NotNull StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());

    byte flags = 0;
    if (stub.isPositionalContainer()) flags |= POSITIONAL_CONTAINER;
    if (stub.isKeywordContainer()) flags |= KEYWORD_CONTAINER;
    dataStream.writeByte(flags);
    dataStream.writeName(stub.getDefaultValueText());
    dataStream.writeName(stub.getTypeComment());
    dataStream.writeName(stub.getAnnotation());
  }

  @Override
  public @NotNull PyNamedParameterStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    byte flags = dataStream.readByte();
    String defaultValueText = dataStream.readNameString();
    String typeComment = dataStream.readNameString();
    String annotation = dataStream.readNameString();
    return new PyNamedParameterStubImpl(name,
                                        (flags & POSITIONAL_CONTAINER) != 0,
                                        (flags & KEYWORD_CONTAINER) != 0,
                                        defaultValueText,
                                        typeComment,
                                        annotation,
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

  protected @NotNull IStubElementType getStubElementType() {
    return PyStubElementTypes.NAMED_PARAMETER;
  }
}