// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.getReturnTypeAnnotation;

public class PyAncestorTypeProvider extends PyTypeProviderBase {

  @Nullable
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final Ref<PyType> typeFromAncestors = getParameterTypeFromSupertype(param, func, context);
    if (typeFromAncestors != null) {
      return typeFromAncestors;
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getParameterTypeFromSupertype(
    @NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final PyFunctionType superFunctionType = getOverriddenFunctionType(func, context);

    if (superFunctionType != null) {
      final PyFunctionType superFunctionTypeRemovedSelf = superFunctionType.dropSelf(context);
      final List<PyCallableParameter> parameters = superFunctionTypeRemovedSelf.getParameters(context);
      if (parameters != null) {
        for (PyCallableParameter parameter : parameters) {
          final String parameterName = parameter.getName();
          if (parameterName != null && parameterName.equals(param.getName())) {
            final PyType pyType = parameter.getType(context);
            if (pyType != null) {
              return new Ref<>(pyType);
            }
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static PyFunctionType getOverriddenFunctionType(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    final PyFunction overriddenFunction = getOverriddenFunction(function, context);
    if (overriddenFunction != null) {
      PyType type = context.getType(overriddenFunction);
      if (type instanceof PyFunctionType) {
        return (PyFunctionType)context.getType(overriddenFunction);
      }
    }

    return null;
  }

  @Nullable
  private static PyFunction getOverriddenFunction(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    final Query<PsiElement> superMethodSearchQuery = PySuperMethodsSearch.search(function, context);
    final PsiElement firstSuperMethod = superMethodSearchQuery.findFirst();

    if (!(firstSuperMethod instanceof PyFunction)) {
      return null;
    }
    final PyFunction superFunction = (PyFunction)firstSuperMethod;

    if (superFunction.getDecoratorList() != null) {
      if (StreamEx.of(superFunction.getDecoratorList().getDecorators())
                  .map(PyDecorator::getName)
                  .nonNull()
                  .anyMatch(PyNames.OVERLOAD::equals)) {
        return null;
      }
    }

    final PyClass superClass = superFunction.getContainingClass();
    if (superClass != null && !PyNames.OBJECT.equals(superClass.getName())) {
      return superFunction;
    }

    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      final PyFunction function = (PyFunction)callable;
      final Ref<PyType> typeFromSupertype = getReturnTypeFromSupertype(function, context);
      if (typeFromSupertype != null) {
        return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded(function, typeFromSupertype.get()));
      }
    }
    return null;
  }

  /**
   * Get function return type from supertype.
   *
   * The only source of type information in current implementation is annotation. This is to avoid false positives,
   * that may arise from non direct type estimations (not from annotation, nor form type comments).
   *
   * TODO: switch to return type direct usage when type information source will be available.
   *
   * @param function
   * @param context
   * @return
   */
  @Nullable
  private static Ref<PyType> getReturnTypeFromSupertype(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    final PyFunction overriddenFunction = getOverriddenFunction(function, context);

    if (overriddenFunction != null) {
      PyExpression superFunctionAnnotation = getReturnTypeAnnotation(overriddenFunction, context);
      if (superFunctionAnnotation != null) {
        final Ref<PyType> typeRef = PyTypingTypeProvider.getType(superFunctionAnnotation, new PyTypingTypeProvider.Context(context));
        if (typeRef != null) {
          return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded(function, typeRef.get()));
        }
      }
    }
    return null;
  }
}
