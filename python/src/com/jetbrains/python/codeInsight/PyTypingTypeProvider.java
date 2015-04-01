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
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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

  private static ImmutableSet<String> GENERIC_CLASSES = ImmutableSet.<String>builder()
    .add("typing.Generic")
    .add("typing.AbstractGeneric")
    .add("typing.Protocol")
    .build();

  @Nullable
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final PyAnnotation annotation = param.getAnnotation();
    if (annotation != null) {
      // XXX: Requires switching from stub to AST
      final PyExpression value = annotation.getValue();
      if (value != null) {
        final PyType type = getTypingType(value, context);
        if (type != null) {
          final PyType optionalType = getOptionalTypeFromDefaultNone(param, type, context);
          return Ref.create(optionalType != null ? optionalType : type);
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      final PyFunction function = (PyFunction)callable;
      final PyAnnotation annotation = function.getAnnotation();
      if (annotation != null) {
        // XXX: Requires switching from stub to AST
        final PyExpression value = annotation.getValue();
        if (value != null) {
          final PyType type = getTypingType(value, context);
          return type != null ? Ref.create(type) : null;
        }
      }
      final PyType constructorType = getGenericConstructorType(function, context);
      if (constructorType != null) {
        return Ref.create(constructorType);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    if ("typing.cast".equals(function.getQualifiedName()) && callSite instanceof PyCallExpression) {
      final PyCallExpression callExpr = (PyCallExpression)callSite;
      final PyExpression[] args = callExpr.getArguments();
      if (args.length > 0) {
        final PyExpression typeExpr = args[0];
        return getTypingType(typeExpr, context);
      }
    }
    return null;
  }

  private static boolean isAny(@NotNull PyType type) {
    return type instanceof PyClassType && "typing.Any".equals(((PyClassType)type).getPyClass().getQualifiedName());
  }

  @Nullable
  private static PyType getOptionalTypeFromDefaultNone(@NotNull PyNamedParameter param,
                                                       @NotNull PyType type,
                                                       @NotNull TypeEvalContext context) {
    final PyExpression defaultValue = param.getDefaultValue();
    if (defaultValue != null) {
      final PyType defaultType = context.getType(defaultValue);
      if (defaultType instanceof PyNoneType) {
        return PyUnionType.union(type, defaultType);
      }
    }
    return null;
  }

  @Nullable
  private static PyType getGenericConstructorType(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    if (PyUtil.isInit(function)) {
      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        final List<PyGenericType> genericTypes = collectGenericTypes(cls, context);

        final PyType elementType;
        if (genericTypes.size() == 1) {
          elementType = genericTypes.get(0);
        }
        else if (genericTypes.size() > 1) {
          elementType = PyTupleType.create(cls, genericTypes.toArray(new PyType[genericTypes.size()]));
        }
        else {
          elementType = null;
        }

        if (elementType != null) {
          return new PyCollectionTypeImpl(cls, false, elementType);
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<PyGenericType> collectGenericTypes(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    boolean isGeneric = false;
    for (PyClass ancestor : cls.getAncestorClasses(context)) {
      if (GENERIC_CLASSES.contains(ancestor.getQualifiedName())) {
        isGeneric = true;
        break;
      }
    }
    if (isGeneric) {
      final ArrayList<PyGenericType> results = new ArrayList<PyGenericType>();
      // XXX: Requires switching from stub to AST
      for (PyExpression expr : cls.getSuperClassExpressions()) {
        if (expr instanceof PySubscriptionExpression) {
          final PyExpression indexExpr = ((PySubscriptionExpression)expr).getIndexExpression();
          if (indexExpr != null) {
            final PyGenericType genericType = getGenericType(indexExpr, context);
            if (genericType != null) {
              results.add(genericType);
            }
          }
        }
      }
      return results;
    }
    return Collections.emptyList();
  }

  @Nullable
  private static PyType getTypingType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final PyType unionType = getUnionType(expression, context);
    if (unionType != null) {
      return unionType;
    }
    final Ref<PyType> optionalType = getOptionalTypeFromDefaultNone(expression, context);
    if (optionalType != null) {
      return optionalType.get();
    }
    final PyType callableType = getCallableType(expression, context);
    if (callableType != null) {
      return callableType;
    }
    final PyType parameterizedType = getParameterizedType(expression, context);
    if (parameterizedType != null) {
      return parameterizedType;
    }
    final PyType builtinCollection = getBuiltinCollection(expression, context);
    if (builtinCollection != null) {
      return builtinCollection;
    }
    final PyType genericType = getGenericType(expression, context);
    if (genericType != null) {
      return genericType;
    }
    final Ref<PyType> classType = getClassType(expression, context);
    if (classType != null) {
      return classType.get();
    }
    final PyType stringBasedType = getStringBasedType(expression, context);
    if (stringBasedType != null) {
      return stringBasedType;
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getClassType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final PyType type = context.getType(expression);
    if (type != null && isAny(type)) {
      return Ref.create();
    }
    if (type instanceof PyClassLikeType) {
      final PyClassLikeType classType = (PyClassLikeType)type;
      if (classType.isDefinition()) {
        final PyType instanceType = classType.toInstance();
        return Ref.create(instanceType);
      }
    }
    else if (type instanceof PyNoneType) {
      return Ref.create(type);
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getOptionalTypeFromDefaultNone(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)expression;
      final PyExpression operand = subscriptionExpr.getOperand();
      final String operandName = resolveToQualifiedName(operand, context);
      if ("typing.Optional".equals(operandName)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr != null) {
          final PyType type = getType(indexExpr, context);
          if (type != null) {
            return Ref.create(PyUnionType.union(type, PyNoneType.INSTANCE));
          }
        }
        return Ref.create();
      }
    }
    return null;
  }

  @Nullable
  private static PyType getStringBasedType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PyStringLiteralExpression) {
      // XXX: Requires switching from stub to AST
      final String contents = ((PyStringLiteralExpression)expression).getStringValue();
      final Project project = expression.getProject();
      final PyExpressionCodeFragmentImpl codeFragment = new PyExpressionCodeFragmentImpl(project, "dummy.py", contents, false);
      codeFragment.setContext(expression.getContainingFile());
      final PsiElement element = codeFragment.getFirstChild();
      if (element instanceof PyExpressionStatement) {
        final PyExpression dummyExpr = ((PyExpressionStatement)element).getExpression();
        return getType(dummyExpr, context);
      }
    }
    return null;
  }

  @Nullable
  private static PyType getCallableType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)expression;
      final PyExpression operand = subscriptionExpr.getOperand();
      final String operandName = resolveToQualifiedName(operand, context);
      if ("typing.Callable".equals(operandName)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr instanceof PyTupleExpression) {
          final PyTupleExpression tupleExpr = (PyTupleExpression)indexExpr;
          final PyExpression[] elements = tupleExpr.getElements();
          if (elements.length == 2) {
            final PyExpression parametersExpr = elements[0];
            if (parametersExpr instanceof PyListLiteralExpression) {
              final List<PyCallableParameter> parameters = new ArrayList<PyCallableParameter>();
              final PyListLiteralExpression listExpr = (PyListLiteralExpression)parametersExpr;
              for (PyExpression argExpr : listExpr.getElements()) {
                parameters.add(new PyCallableParameterImpl(null, getType(argExpr, context)));
              }
              final PyExpression returnTypeExpr = elements[1];
              final PyType returnType = getType(returnTypeExpr, context);
              return new PyCallableTypeImpl(parameters, returnType);
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getUnionType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)expression;
      final PyExpression operand = subscriptionExpr.getOperand();
      final String operandName = resolveToQualifiedName(operand, context);
      if ("typing.Union".equals(operandName)) {
        return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
      }
    }
    return null;
  }

  @Nullable
  private static PyGenericType getGenericType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final PsiElement resolved = resolve(expression, context);
    if (resolved instanceof PyTargetExpression) {
      final PyTargetExpression targetExpr = (PyTargetExpression)resolved;
      final QualifiedName calleeName = targetExpr.getCalleeName();
      if (calleeName != null && "TypeVar".equals(calleeName.toString())) {
        // XXX: Requires switching from stub to AST
        final PyExpression assigned = targetExpr.findAssignedValue();
        if (assigned instanceof PyCallExpression) {
          final PyCallExpression assignedCall = (PyCallExpression)assigned;
          final PyExpression callee = assignedCall.getCallee();
          if (callee != null) {
            final String calleeQName = resolveToQualifiedName(callee, context);
            if ("typing.TypeVar".equals(calleeQName)) {
              final PyExpression[] arguments = assignedCall.getArguments();
              if (arguments.length > 0) {
                final PyExpression firstArgument = arguments[0];
                if (firstArgument instanceof PyStringLiteralExpression) {
                  final String name = ((PyStringLiteralExpression)firstArgument).getStringValue();
                  if (name != null) {
                    return new PyGenericType(name, getGenericTypeBound(arguments, context));
                  }
                }
              }
            }

          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getGenericTypeBound(@NotNull PyExpression[] typeVarArguments, @NotNull TypeEvalContext context) {
    final List<PyType> types = new ArrayList<PyType>();
    for (int i = 1; i < typeVarArguments.length; i++) {
      types.add(getType(typeVarArguments[i], context));
    }
    return PyUnionType.union(types);
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
  private static PyType getParameterizedType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)expression;
      final PyExpression operand = subscriptionExpr.getOperand();
      final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
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
    return null;
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
  private static PsiElement resolve(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PyReferenceOwner) {
      final PyReferenceOwner referenceOwner = (PyReferenceOwner)expression;
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
      final PsiPolyVariantReference reference = referenceOwner.getReference(resolveContext);
      final PsiElement element = reference.resolve();
      if (element instanceof PyFunction) {
        final PyFunction function = (PyFunction)element;
        if (PyUtil.isInit(function)) {
          final PyClass cls = function.getContainingClass();
          if (cls != null) {
            return cls;
          }
        }
      }
      return element;
    }
    return null;
  }

  @Nullable
  private static String resolveToQualifiedName(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final PsiElement element = resolve(expression, context);
    if (element instanceof PyQualifiedNameOwner) {
      final PyQualifiedNameOwner qualifiedNameOwner = (PyQualifiedNameOwner)element;
      return qualifiedNameOwner.getQualifiedName();
    }
    return null;
  }
}
