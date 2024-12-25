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

import com.google.common.collect.RangeSet;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction> {
  public PyFunctionElementType() {
    this("FUNCTION_DECLARATION");
  }

  public PyFunctionElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull PsiElement createElement(final @NotNull ASTNode node) {
    return new PyFunctionImpl(node);
  }

  @Override
  public PyFunction createPsi(final @NotNull PyFunctionStub stub) {
    return new PyFunctionImpl(stub);
  }

  @Override
  public @NotNull PyFunctionStub createStub(final @NotNull PyFunction psi, final StubElement parentStub) {
    final PyFunctionImpl function = (PyFunctionImpl)psi;
    final String message = function.extractDeprecationMessage();
    final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
    final String typeComment = function.getTypeCommentAnnotation();
    final String annotationContent = function.getAnnotationValue();
    final RangeSet<Version> versions = PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi);
    return new PyFunctionStubImpl(psi.getName(),
                                  PyPsiUtils.strValue(docStringExpression),
                                  message,
                                  function.isAsync(),
                                  function.isGenerator(),
                                  function.onlyRaisesNotImplementedError(),
                                  typeComment,
                                  annotationContent,
                                  parentStub,
                                  getStubElementType(),
                                  versions);
  }

  @Override
  public void serialize(final @NotNull PyFunctionStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeUTFFast(StringUtil.notNullize(stub.getDocString()));
    dataStream.writeName(stub.getDeprecationMessage());
    dataStream.writeBoolean(stub.isAsync());
    dataStream.writeBoolean(stub.isGenerator());
    dataStream.writeBoolean(stub.onlyRaisesNotImplementedError());
    dataStream.writeName(stub.getTypeComment());
    dataStream.writeName(stub.getAnnotation());
    PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
  }

  @Override
  public @NotNull PyFunctionStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    String docString = dataStream.readUTFFast();
    String deprecationMessage = dataStream.readNameString();
    final boolean isAsync = dataStream.readBoolean();
    final boolean isGenerator = dataStream.readBoolean();
    final boolean onlyRaisesNotImplementedError = dataStream.readBoolean();
    String typeComment = dataStream.readNameString();
    String annotationContent = dataStream.readNameString();
    RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);
    return new PyFunctionStubImpl(name,
                                  StringUtil.nullize(docString),
                                  deprecationMessage,
                                  isAsync,
                                  isGenerator,
                                  onlyRaisesNotImplementedError,
                                  typeComment,
                                  annotationContent,
                                  parentStub,
                                  getStubElementType(),
                                  versions);
  }

  @Override
  public void indexStub(final @NotNull PyFunctionStub stub, final @NotNull IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyFunctionNameIndex.KEY, name);
      if (stub.getParentStub() instanceof PyFileStub && PyUtil.getInitialUnderscores(name) == 0) {
        sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
      }
    }
  }

  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.FUNCTION_DECLARATION;
  }
}