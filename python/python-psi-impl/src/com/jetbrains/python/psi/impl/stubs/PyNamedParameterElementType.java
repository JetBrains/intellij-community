// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
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
  public @NotNull PyNamedParameterStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub)
    throws IOException {
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