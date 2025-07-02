// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;


public interface PyTypeProvider {
  ExtensionPointName<PyTypeProvider> EP_NAME = ExtensionPointName.create("Pythonid.typeProvider");

  @Nullable
  PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context);

  @Nullable
  Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor);

  @Nullable
  Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context);

  @Nullable
  Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context);

  @Nullable
  Ref<PyType> getCallType(@NotNull PyFunction function, @NotNull PyCallSiteExpression callSite, @NotNull TypeEvalContext context);

  @Nullable
  PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context);

  @Nullable
  PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context);

  /**
   * Returns a parameterized version of the class type.
   *
   * E.g. for class C(Generic[T], B[Tuple[T, str]]) it should return C[T].
   */
  @Nullable
  PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context);

  /**
   * Returns a map of generic substitutions for the base classes.
   *
   * E.g. for class C(Generic[T], B[Tuple[T, str]]) where B(Generic[V]) it should return {V: Tuple[T, str]}.
   */
  @NotNull
  Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull TypeEvalContext context);

  /**
   * <p>
   * If callee type is a class type, it is replaced in code insight for {@code call}
   * with {@code __init__}/{@code __new__} or {@code __call__} depending on whether it is a definition or an instance.
   * </p>
   * <p>
   * If the {@code type} is provided, and it is desirable to stay with the provided {@code type}, please wrap it into {@link Ref}.
   * </p>
   * <p>
   * If code insight should be suppressed for the {@code call}, return {@code null} wrapped into the {@link Ref}.
   * </p>
   * <p>
   * Return {@code null} otherwise.
   * </p>
   */
  @Nullable Ref<@Nullable PyCallableType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                   @NotNull PyCallExpression call,
                                                                   @NotNull TypeEvalContext context);

  @ApiStatus.Experimental
  @Nullable List<@NotNull PyTypedResolveResult> getMemberTypes(@NotNull PyType type,
                                                               @NotNull String name,
                                                               @Nullable PyExpression location,
                                                               @NotNull AccessDirection direction,
                                                               @NotNull PyResolveContext context);
}
