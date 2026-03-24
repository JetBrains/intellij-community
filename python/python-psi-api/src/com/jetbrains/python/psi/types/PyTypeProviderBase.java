// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class PyTypeProviderBase implements PyTypeProvider {

  @Override
  public @Nullable PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    return null;
  }

  @Override
  public @Nullable Ref<PyType> getParameterType(@NotNull PyNamedParameter param,
                                                @NotNull PyFunction func,
                                                @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public @Nullable Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public @Nullable Ref<PyType> getCallType(@NotNull PyFunction function,
                                           @NotNull PyCallSiteExpression callSite,
                                           @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public @Nullable PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context) {
    return null;
  }

  @Override
  public @Nullable PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public @Nullable PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public @NotNull Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return Collections.emptyMap();
  }

  @Override
  public @Nullable Ref<@Nullable PyCallableType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                          @NotNull PyCallExpression call,
                                                                          @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public @Nullable List<@NotNull PyTypeMember> getMemberTypes(@NotNull PyType type,
                                                              @NotNull String name,
                                                              @Nullable PyExpression location,
                                                              @NotNull AccessDirection direction,
                                                              @NotNull PyResolveContext context) {
    return null;
  }
}
