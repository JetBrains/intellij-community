// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

/**
 * @author Mikhail Golubev
 */
public final class PyDocStringTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    StructuredDocString docString = func.getStructuredDocString();
    if (docString == null) {
      final PyClass pyClass = PyUtil.turnConstructorIntoClass(func);
      if (pyClass != null) {
        docString = pyClass.getStructuredDocString();
      }
    }
    if (docString != null) {
      final String typeText = docString.getParamType(param.getName());
      if (StringUtil.isNotEmpty(typeText)) {
        final Ref<PyType> typeRef = parseType(func, typeText, context);

        if (param.isPositionalContainer()) {
          return Ref.create(PyTypeUtil.toPositionalContainerType(param, typeRef.get()));
        }

        if (param.isKeywordContainer()) {
          return Ref.create(PyTypeUtil.toKeywordContainerType(param, typeRef.get()));
        }

        return typeRef;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyDocStringOwner) {
      final StructuredDocString docString = ((PyDocStringOwner)callable).getStructuredDocString();
      if (docString != null) {
        final String typeText = docString.getReturnType();
        if (StringUtil.isNotEmpty(typeText)) {
          final Ref<PyType> typeRef = parseType(callable, typeText, context);
          if (callable instanceof PyFunction) {
            return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded((PyFunction)callable, typeRef.get()));
          }

          return typeRef;
        }
      }
    }
    return null;
  }

  @NotNull
  private Ref<PyType> parseType(@NotNull PyCallable callable, @NotNull String typeText, @NotNull TypeEvalContext context) {
    final PyType type = PyTypeParser.getTypeByName(callable, typeText, context);
    if (type != null) {
      type.assertValid("from docstring");
    }
    setTypeVarScopeOwners(type, callable, context);
    return Ref.create(type);
  }

  /**
   * Unify generics in the constructor according to the legacy type hints syntax.
   */
  @Nullable
  @Override
  public PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    PyFunction init = cls.findInitOrNew(true, context);
    if (init != null) {
      PyType returnType = Ref.deref(getReturnType(init, context));
      if (returnType instanceof PyCollectionType) {
        return returnType;
      }
    }

    return null;
  }

  // A hack to update scope owners of type parameters parsed out of docstrings
  private void setTypeVarScopeOwners(@Nullable PyType type, @NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (!(callable instanceof PyFunction pyFunction)) return;
    PyTypeChecker.Generics typeParameters = PyTypeChecker.collectGenerics(type, context);
    if (typeParameters.isEmpty()) return;

    PyClass pyClass = pyFunction.getContainingClass();

    Function<PyGenericType, PyQualifiedNameOwner> findScopeOwner = typeVar -> pyFunction;
    if (PyUtil.isInitOrNewMethod(callable)) {
      findScopeOwner = typeVar -> pyClass;
    }
    else if (pyClass != null) {
      PyType classGenericType = getGenericType(pyClass, context);
      if (classGenericType != null) {
        PyTypeChecker.Generics classTypeParameters = PyTypeChecker.collectGenerics(classGenericType, context);
        Set<String> classTypeVarNames = ContainerUtil.map2Set(classTypeParameters.getTypeVars(), PyGenericType::getName);
        findScopeOwner = typeVar -> classTypeVarNames.contains(typeVar.getName()) ? pyClass : pyFunction;
      }
    }

    for (PyTypeParameterType typeParam : typeParameters.getAllTypeParameters()) {
      if (typeParam instanceof PyTypeVarTypeImpl typeVar) {
        typeVar.setScopeOwner(findScopeOwner.apply(typeVar));
      }
    }
  }
}
