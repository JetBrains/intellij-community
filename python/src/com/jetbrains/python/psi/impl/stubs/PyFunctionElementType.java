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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction> {
  public PyFunctionElementType() {
    this("FUNCTION_DECLARATION");
  }

  public PyFunctionElementType(String debugName) {
    super(debugName);
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyFunctionImpl(node);
  }

  public PyFunction createPsi(@NotNull final PyFunctionStub stub) {
    return new PyFunctionImpl(stub);
  }

  public PyFunctionStub createStub(@NotNull final PyFunction psi, final StubElement parentStub) {
    PyFunctionImpl function = (PyFunctionImpl)psi;
    String message = function.extractDeprecationMessage();
    final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
    final String typeComment = function.getTypeCommentAnnotation();
    return new PyFunctionStubImpl(psi.getName(), PyPsiUtils.strValue(docStringExpression),
                                  message, 
                                  function.isAsync(), 
                                  typeComment,
                                  parentStub,
                                  getStubElementType());
  }

  public void serialize(@NotNull final PyFunctionStub stub, @NotNull final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeUTFFast(StringUtil.notNullize(stub.getDocString()));
    dataStream.writeName(stub.getDeprecationMessage());
    dataStream.writeBoolean(stub.isAsync());
    dataStream.writeName(stub.getTypeComment());
  }

  @NotNull
  public PyFunctionStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    String docString = dataStream.readUTFFast();
    StringRef deprecationMessage = dataStream.readName();
    final boolean isAsync = dataStream.readBoolean();
    final StringRef typeComment = dataStream.readName();
    return new PyFunctionStubImpl(name, StringUtil.nullize(docString), 
                                  deprecationMessage == null ? null : deprecationMessage.getString(),
                                  isAsync, 
                                  typeComment == null ? null : typeComment.getString(),
                                  parentStub,
                                  getStubElementType());
  }

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