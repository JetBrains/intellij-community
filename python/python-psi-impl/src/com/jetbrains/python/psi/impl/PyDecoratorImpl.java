// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyDecoratorImpl extends PyBaseElementImpl<PyDecoratorStub> implements PyDecorator {

  public PyDecoratorImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyDecoratorImpl(PyDecoratorStub stub) {
    super(stub, PyElementTypes.DECORATOR_CALL);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDecorator(this);
  }

  /**
   * @return the name of decorator, without the "@". Stub is used if available.
   */
  @Override
  public String getName() {
    return PyDecorator.super.getName();
  }

  @Override
  public boolean isBuiltin() {
    ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens());
    if (node != null) {
      PyReferenceExpression ref = (PyReferenceExpression)node.getPsi();
      PsiElement target = ref.getReference().resolve();
      return PyBuiltinCache.getInstance(this).isBuiltin(target);
    }
    return false;
  }

  @Override
  public boolean hasArgumentList() {
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.hasArgumentList();
    }
    else {
      return getExpression() instanceof PyCallExpression;
    }
  }

  @Override
  @Nullable
  public QualifiedName getQualifiedName() {
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    else {
      return PyDecorator.super.getQualifiedName();
    }
  }

  @NotNull
  @Override
  public List<PyCallableType> multiResolveCallee(@NotNull PyResolveContext resolveContext) {
    final Function<PyCallableType, PyCallableType> mapping = callableType -> {
      if (!hasArgumentList()) {
        // NOTE: that +1 thing looks fishy
        final TypeEvalContext context = resolveContext.getTypeEvalContext();
        return new PyCallableTypeImpl(callableType.getParameters(context),
                                      callableType.getReturnType(context),
                                      callableType.getCallable(),
                                      callableType.getModifier(),
                                      callableType.getImplicitOffset() + 1);
      }

      return callableType;
    };

    return ContainerUtil.map(PyCallExpressionHelper.multiResolveCallee(this, resolveContext), mapping);
  }

  @NotNull
  @Override
  public List<PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext) {
    return PyCallExpressionHelper.mapArguments(this, resolveContext);
  }

  @Override
  public String toString() {
    return "PyDecorator: @" + PyUtil.getReadableRepr(getCallee(), true); //getCalledFunctionReference().getReferencedName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = getNode();
    final ASTNode nameNode = node.findChildByType(PyTokenTypes.IDENTIFIER);
    if (nameNode != null) {
      final ASTNode nameElement = PyUtil.createNewName(this, name);
      node.replaceChild(nameNode, nameElement);
      return this;
    }
    else {
      throw new IncorrectOperationException("No name node");
    }
  }

  @Override
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCallExpressionHelper.getCallType(this, context, key);
  }
}
