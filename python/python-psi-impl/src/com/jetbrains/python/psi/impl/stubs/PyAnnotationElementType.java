// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyAnnotationImpl;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyAnnotationElementType extends PyStubElementType<PyAnnotationStub, PyAnnotation> {
  public PyAnnotationElementType() {
    this("ANNOTATION");
  }

  public PyAnnotationElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  public PyAnnotation createPsi(@NotNull final PyAnnotationStub stub) {
    return new PyAnnotationImpl(stub);
  }

  @Override
  @NotNull
  public PyAnnotationStub createStub(@NotNull final PyAnnotation psi, final StubElement parentStub) {
    return new PyAnnotationStubImpl(parentStub, PyStubElementTypes.ANNOTATION);
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyAnnotationImpl(node);
  }

  @Override
  public void serialize(@NotNull final PyAnnotationStub stub, @NotNull final StubOutputStream dataStream)
      throws IOException {
  }

  @Override
  @NotNull
  public PyAnnotationStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PyAnnotationStubImpl(parentStub, PyStubElementTypes.ANNOTATION);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    final IElementType parentType = node.getTreeParent().getElementType();
    return PythonDialectsTokenSetProvider.getInstance().getFunctionDeclarationTokens().contains(parentType)
           || PythonDialectsTokenSetProvider.getInstance().getParameterTokens().contains(parentType);
  }
}