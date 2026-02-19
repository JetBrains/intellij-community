// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyDecoratorImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Actual serialized data of a decorator call.
 */
public class PyDecoratorCallElementType extends PyStubElementType<PyDecoratorStub, PyDecorator>
  implements PyCustomizableStubElementType<PyDecorator, PyCustomDecoratorStub, PyCustomDecoratorStubType<? extends PyCustomDecoratorStub>> {
  public PyDecoratorCallElementType() {
    super("DECORATOR_CALL");
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return new PyDecoratorImpl(node);
  }

  @Override
  public PyDecorator createPsi(@NotNull PyDecoratorStub stub) {
    return new PyDecoratorImpl(stub);
  }

  @Override
  public @NotNull PyDecoratorStub createStub(@NotNull PyDecorator psi, StubElement parentStub) {
    return new PyDecoratorStubImpl(psi.getQualifiedName(), psi.hasArgumentList(), parentStub, createCustomStub(psi));
  }

  @Override
  public @NotNull List<PyCustomDecoratorStubType<? extends PyCustomDecoratorStub>> getExtensions() {
    return PyCustomDecoratorStubType.EP_NAME.getExtensionList();
  }

  @Override
  public void serialize(@NotNull PyDecoratorStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    QualifiedName.serialize(stub.getQualifiedName(), dataStream);
    dataStream.writeBoolean(stub.hasArgumentList());
    PyCustomDecoratorStub customStub = stub.getCustomStub(PyCustomDecoratorStub.class);
    serializeCustomStub(customStub, dataStream);
  }

  @Override
  public void indexStub(final @NotNull PyDecoratorStub stub, final @NotNull IndexSink sink) {
    // Index decorators stub by name (todo: index by FQDN as well!)
    final QualifiedName qualifiedName = stub.getQualifiedName();
    if (qualifiedName != null) {
      sink.occurrence(PyDecoratorStubIndex.KEY, qualifiedName.toString());
    }
  }

  @Override
  public @NotNull PyDecoratorStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    QualifiedName q_name = QualifiedName.deserialize(dataStream);
    boolean hasArgumentList = dataStream.readBoolean();
    PyCustomDecoratorStub customStub = deserializeCustomStub(dataStream);
    return new PyDecoratorStubImpl(q_name, hasArgumentList, parentStub, customStub);
  }
}
