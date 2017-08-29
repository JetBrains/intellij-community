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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a tuple parameter as stubbed element.
 */
public class PyTupleParameterImpl extends PyBaseElementImpl<PyTupleParameterStub> implements PyTupleParameter {
  
  public PyTupleParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTupleParameterImpl(PyTupleParameterStub stub) {
    super(stub, PyElementTypes.TUPLE_PARAMETER);
  }

  @Override
  @Nullable
  public PyNamedParameter getAsNamed() {
    return null;  // we're not named
  }

  @Override
  @NotNull
  public PyTupleParameter getAsTuple() {
    return this;
  }

  @Override
  @Nullable
  public PyExpression getDefaultValue() {
    ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    if (nodes.length > 0) {
      return (PyExpression)nodes[0].getPsi();
    }
    return null;
  }

  @Override
  public boolean hasDefaultValue() {
    final PyTupleParameterStub stub = getStub();
    if (stub != null) {
      return stub.hasDefaultValue();
    }
    return getDefaultValue() != null;
  }

  @Override
  @Nullable
  public String getDefaultValueText() {
    final PyTupleParameterStub stub = getStub();
    if (stub != null) {
      return stub.getDefaultValueText();
    }
    return ParamHelper.getDefaultValueText(getDefaultValue());
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleParameter(this);
  }

  @Override
  @NotNull
  public PyParameter[] getContents() {
    return getStubOrPsiChildren(PythonDialectsTokenSetProvider.INSTANCE.getParameterTokens(), new PyParameter[0]);
  }

  @Override
  public boolean isSelf() {
    return false;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this);
  }
}
