// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a tuple parameter as stubbed element.
 */
@ApiStatus.Internal
public class PyTupleParameterImpl extends PyBaseElementImpl<PyTupleParameterStub> implements PyTupleParameter {

  public PyTupleParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTupleParameterImpl(PyTupleParameterStub stub) {
    super(stub, PyStubElementTypes.TUPLE_PARAMETER);
  }

  @Override
  public boolean hasDefaultValue() {
    final PyTupleParameterStub stub = getStub();
    if (stub != null) {
      return stub.getDefaultValueText() != null;
    }
    return PyTupleParameter.super.hasDefaultValue();
  }

  @Override
  public @Nullable String getDefaultValueText() {
    final PyTupleParameterStub stub = getStub();
    if (stub != null) {
      return stub.getDefaultValueText();
    }
    return PyTupleParameter.super.getDefaultValueText();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleParameter(this);
  }

  @Override
  public PyParameter @NotNull [] getContents() {
    return getStubOrPsiChildren(PythonDialectsTokenSetProvider.getInstance().getParameterTokens(), new PyParameter[0]);
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return new PyElementPresentation(this);
  }
}
