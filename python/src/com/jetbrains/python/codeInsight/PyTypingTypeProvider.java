/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyTypingTypeProvider extends PyTypeProviderBase {
  private static ImmutableMap<String, String> BUILTIN_COLLECTIONS = ImmutableMap.<String, String>builder()
    .put("typing.List", "list")
    .put("typing.Dict", "dict")
    .put("typing.Set", PyNames.SET)
    .put("typing.Tuple", PyNames.TUPLE)
    .build();

  public PyType getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final PyAnnotation annotation = param.getAnnotation();
    if (annotation != null) {
      final PyExpression value = annotation.getValue();
      if (value != null) {
        return getTypingType(value, context);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull Callable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      final PyFunction function = (PyFunction)callable;
      final PyAnnotation annotation = function.getAnnotation();
      if (annotation != null) {
        final PyExpression value = annotation.getValue();
        if (value != null) {
          return getTypingType(value, context);
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getTypingType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    // TODO: Put the annotation text into stubs and parse it to avoid switching from stubs to AST
    if (expression instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)expression;
      final PyExpression operand = subscriptionExpr.getOperand();
      final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
      final String operandName = resolveToQualifiedName(operand, context);
      if ("typing.Union".equals(operandName)) {
        return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
      }
      else {
        final PyType operandType = getType(operand, context);
        if (operandType instanceof PyClassType) {
          final PyClass cls = ((PyClassType)operandType).getPyClass();
          if (PyNames.TUPLE.equals(cls.getQualifiedName())) {
            final List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
            return PyTupleType.create(expression, indexTypes.toArray(new PyType[indexTypes.size()]));
          }
          else if (indexExpr != null) {
            final PyType indexType = context.getType(indexExpr);
            return new PyCollectionTypeImpl(cls, false, indexType);
          }
        }
      }
    }
    else {
      final PyType builtinCollection = getBuiltinCollection(expression, context);
      if (builtinCollection != null) {
        return builtinCollection;
      }
    }
    return null;
  }

  @NotNull
  private static List<PyType> getIndexTypes(@NotNull PySubscriptionExpression expression, @NotNull TypeEvalContext context) {
    final List<PyType> types = new ArrayList<PyType>();
    final PyExpression indexExpr = expression.getIndexExpression();
    if (indexExpr instanceof PyTupleExpression) {
      final PyTupleExpression tupleExpr = (PyTupleExpression)indexExpr;
      for (PyExpression expr : tupleExpr.getElements()) {
        types.add(getType(expr, context));
      }
    }
    return types;
  }

  @Nullable
  private static PyType getBuiltinCollection(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final String collectionName = resolveToQualifiedName(expression, context);
    final String builtinName = BUILTIN_COLLECTIONS.get(collectionName);
    return builtinName != null ? PyTypeParser.getTypeByName(expression, builtinName) : null;
  }

  @Nullable
  private static PyType getType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    // It is possible to replace PyAnnotation.getType() with this implementation
    final PyType typingType = getTypingType(expression, context);
    if (typingType != null) {
      return typingType;
    }
    final PyType type = context.getType(expression);
    if (type instanceof PyClassLikeType) {
      final PyClassLikeType classType = (PyClassLikeType)type;
      if (classType.isDefinition()) {
        return classType.toInstance();
      }
    }
    else if (type instanceof PyNoneType) {
      return type;
    }
    return null;
  }

  @Nullable
  private static String resolveToQualifiedName(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PyReferenceOwner) {
      final PyReferenceOwner referenceOwner = (PyReferenceOwner)expression;
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
      final PsiPolyVariantReference reference = referenceOwner.getReference(resolveContext);
      final PsiElement element = reference.resolve();
      if (element instanceof PyQualifiedNameOwner) {
        final PyQualifiedNameOwner qualifiedNameOwner = (PyQualifiedNameOwner)element;
        return qualifiedNameOwner.getQualifiedName();
      }
    }
    return null;
  }
}
