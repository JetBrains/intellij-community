/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyQualifiedNameFactory;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass> {
  public PyClassElementType() {
    this("CLASS_DECLARATION");
  }

  public PyClassElementType(String debugName) {
    super(debugName);
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyClassImpl(node);
  }

  public PyClass createPsi(@NotNull final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  public PyClassStub createStub(@NotNull final PyClass psi, final StubElement parentStub) {
    final PyExpression[] exprs = psi.getSuperClassExpressions();
    List<QualifiedName> superClasses = new ArrayList<QualifiedName>();
    for (PyExpression expression : exprs) {
      expression = PyClassImpl.unfoldClass(expression);
      superClasses.add(PyQualifiedNameFactory.fromExpression(expression));
    }
    final PyStringLiteralExpression docStringExpression = psi.getDocStringExpression();
    return new PyClassStubImpl(psi.getName(), parentStub,
                               superClasses.toArray(new QualifiedName[superClasses.size()]),
                               psi.getOwnSlots(),
                               PyPsiUtils.strValue(docStringExpression),
                               getStubElementType());
  }

  public void serialize(@NotNull final PyClassStub pyClassStub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());
    final QualifiedName[] classes = pyClassStub.getSuperClasses();
    dataStream.writeByte(classes.length);
    for (QualifiedName s : classes) {
      QualifiedName.serialize(s, dataStream);
    }
    PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());
    final String docString = pyClassStub.getDocString();
    dataStream.writeUTFFast(docString != null ? docString : "");
  }

  @NotNull
  public PyClassStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    int superClassCount = dataStream.readByte();
    QualifiedName[] superClasses = new QualifiedName[superClassCount];
    for (int i = 0; i < superClassCount; i++) {
      superClasses[i] = QualifiedName.deserialize(dataStream);
    }
    List<String> slots = PyFileElementType.readNullableList(dataStream);
    final String docString = dataStream.readUTFFast();
    return new PyClassStubImpl(name, parentStub, superClasses, slots, docString.length() > 0 ? docString : null, getStubElementType());
  }

  public void indexStub(@NotNull final PyClassStub stub, @NotNull final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
      sink.occurrence(PyClassNameIndexInsensitive.KEY, name.toLowerCase());
    }
    for (QualifiedName s : stub.getSuperClasses()) {
      if (s != null) {
        String className = s.getLastComponent();
        if (className != null) {
          sink.occurrence(PySuperClassIndex.KEY, className);
        }
      }
    }
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.CLASS_DECLARATION;
  }
}