// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyDocStringTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    StructuredDocString docString = func.getStructuredDocString();
    if (PyNames.INIT.equals(func.getName()) && docString == null) {
      final PyClass pyClass = func.getContainingClass();
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
  private static Ref<PyType> parseType(@NotNull PyCallable callable, @NotNull String typeText, @NotNull TypeEvalContext context) {
    final PyType type = PyTypeParser.getTypeByName(callable, typeText, context);
    if (type != null) {
      type.assertValid("from docstring");
    }
    return Ref.create(type);
  }

  /**
   * Unify generics in the constructor according to the legacy type hints syntax.
   */
  @Nullable
  @Override
  public PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyFunction init = cls.findInitOrNew(true, context);
    if (init != null) {
      final PyCallableType callableType = PyUtil.as(context.getType(init), PyCallableType.class);
      if (callableType != null) {
        final PyType returnType = PyUtil.as(callableType.getReturnType(context), PyCollectionType.class);
        if (returnType != null) {
          return returnType;
        }
      }
    }

    return null;
  }
}
