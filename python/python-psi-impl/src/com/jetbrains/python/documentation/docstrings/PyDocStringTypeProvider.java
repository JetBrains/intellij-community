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

  @Override
  public @Nullable Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
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

  private @NotNull Ref<PyType> parseType(@NotNull PyCallable callable, @NotNull String typeText, @NotNull TypeEvalContext context) {
    final PyType type = PyTypeParser.getTypeByName(callable, typeText, context);
    if (type != null) {
      type.assertValid("from docstring");
    }
    if (callable instanceof PyFunction pyFunction) {
      return Ref.create(PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
        @Override
        public PyType visitPyTypeVarType(@NotNull PyTypeVarType typeVarType) {
          if (typeVarType instanceof PyTypeVarTypeImpl impl) {
            return impl.withScopeOwner(findScopeOwner(typeVarType, pyFunction, context));
          }
          return typeVarType;
        }
      }));
    }
    return Ref.create(type);
  }

  /**
   * Unify generics in the constructor according to the legacy type hints syntax.
   */
  @Override
  public @Nullable PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    PyFunction init = cls.findInitOrNew(true, context);
    if (init != null) {
      PyType returnType = Ref.deref(getReturnType(init, context));
      if (returnType instanceof PyCollectionType) {
        return returnType;
      }
    }

    return null;
  }

  private PyQualifiedNameOwner findScopeOwner(@NotNull PyTypeVarType typeVar,
                                              @NotNull PyFunction function,
                                              @NotNull TypeEvalContext context) {
    PyClass pyClass = function.getContainingClass();
    if (PyUtil.isInitOrNewMethod(function)) {
      return pyClass;
    }
    else if (pyClass != null) {
      PyType classGenericType = getGenericType(pyClass, context);
      if (classGenericType != null) {
        PyTypeChecker.Generics classTypeParameters = PyTypeChecker.collectGenerics(classGenericType, context);
        Set<String> classTypeVarNames = ContainerUtil.map2Set(classTypeParameters.getTypeVars(), PyTypeVarType::getName);
        return classTypeVarNames.contains(typeVar.getName()) ? pyClass : function;
      }
    }
    return function;
  }
}
