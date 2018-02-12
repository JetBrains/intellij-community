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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction> {
  public PyFunctionElementType() {
    this("FUNCTION_DECLARATION");
  }

  public PyFunctionElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyFunctionImpl(node);
  }

  @Override
  public PyFunction createPsi(@NotNull final PyFunctionStub stub) {
    return new PyFunctionImpl(stub);
  }

  @Override
  @NotNull
  public PyFunctionStub createStub(@NotNull final PyFunction psi, final StubElement parentStub) {
    final PyFunctionImpl function = (PyFunctionImpl)psi;
    final String message = function.extractDeprecationMessage();
    final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
    final String typeComment = function.getTypeCommentAnnotation();
    final String annotationContent = function.getAnnotationValue();
    return new PyFunctionStubImpl(psi.getName(),
                                  PyPsiUtils.strValue(docStringExpression),
                                  message,
                                  function.isAsync(),
                                  function.isGenerator(),
                                  function.onlyRaisesNotImplementedError(),
                                  typeComment,
                                  annotationContent,
                                  parentStub,
                                  getStubElementType());
  }

  @Override
  public void serialize(@NotNull final PyFunctionStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeUTFFast(StringUtil.notNullize(stub.getDocString()));
    dataStream.writeName(stub.getDeprecationMessage());
    dataStream.writeBoolean(stub.isAsync());
    dataStream.writeBoolean(stub.isGenerator());
    dataStream.writeBoolean(stub.onlyRaisesNotImplementedError());
    dataStream.writeName(stub.getTypeComment());
    dataStream.writeName(stub.getAnnotation());
  }

  @Override
  @NotNull
  public PyFunctionStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    String docString = dataStream.readUTFFast();
    StringRef deprecationMessage = dataStream.readName();
    final boolean isAsync = dataStream.readBoolean();
    final boolean isGenerator = dataStream.readBoolean();
    final boolean onlyRaisesNotImplementedError = dataStream.readBoolean();
    final StringRef typeComment = dataStream.readName();
    final StringRef annotationContent = dataStream.readName();
    return new PyFunctionStubImpl(name,
                                  StringUtil.nullize(docString),
                                  deprecationMessage == null ? null : deprecationMessage.getString(),
                                  isAsync,
                                  isGenerator,
                                  onlyRaisesNotImplementedError,
                                  typeComment == null ? null : typeComment.getString(),
                                  annotationContent == null ? null : annotationContent.getString(),
                                  parentStub,
                                  getStubElementType());
  }

  @Override
  public void indexStub(@NotNull final PyFunctionStub stub, @NotNull final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyFunctionNameIndex.KEY, name);
    }
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.FUNCTION_DECLARATION;
  }
}