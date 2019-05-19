/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
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
}
