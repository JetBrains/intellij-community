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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author vlan
 */
public class PyTypingTypeProvider extends PyTypeProviderBase {
  public static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile("# *type: *(.*)");

  private static ImmutableMap<String, String> COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
    .put("typing.List", "list")
    .put("typing.Dict", "dict")
    .put("typing.Set", PyNames.SET)
    .put("typing.FrozenSet", "frozenset")
    .put("typing.Tuple", PyNames.TUPLE)
    .put("typing.Iterable", PyNames.COLLECTIONS + "." + PyNames.ITERABLE)
    .put("typing.Iterator", PyNames.COLLECTIONS + "." + PyNames.ITERATOR)
    .put("typing.Container", PyNames.COLLECTIONS + "." + PyNames.CONTAINER)
    .put("typing.Sequence", PyNames.COLLECTIONS + "." + PyNames.SEQUENCE)
    .put("typing.MutableSequence", PyNames.COLLECTIONS + "." + "MutableSequence")
    .put("typing.Mapping", PyNames.COLLECTIONS + "." + PyNames.MAPPING)
    .put("typing.MutableMapping", PyNames.COLLECTIONS + "." + "MutableMapping")
    .put("typing.AbstractSet", PyNames.COLLECTIONS + "." + "Set")
    .put("typing.MutableSet", PyNames.COLLECTIONS + "." + "MutableSet")
    .build();

  public static ImmutableMap<String, String> TYPING_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
    .put("list", "List")
    .put("dict", "Dict")
    .put("set", "Set")
    .put("frozenset", "FrozenSet")
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
        final PyType type = getType(value, context);
        if (type != null) {
          final PyType optionalType = getOptionalTypeFromDefaultNone(param, type, context);
          return Ref.create(optionalType != null ? optionalType : type);
        }
      }
    }
    final String comment = func.getTypeCommentAnnotation();
    if (comment != null) {
      final PyTypeParser.ParseResult result = PyTypeParser.parsePep484FunctionTypeComment(param, comment);
      final PyCallableType functionType = as(result.getType(), PyCallableType.class);
      if (functionType != null) {
        final List<PyCallableParameter> paramTypes = functionType.getParameters(context);
        assert paramTypes != null;
        final PyParameter[] funcParams = func.getParameterList().getParameters();
        final int startOffset = func.getContainingClass() != null ? 1 : 0;
        for (int paramIndex = 0; paramIndex < funcParams.length; paramIndex++) {
          if (funcParams[paramIndex] == param) {
            final int typeIndex = paramIndex - startOffset;
            if (typeIndex >= 0 && typeIndex < paramTypes.size()) {
              return Ref.create(paramTypes.get(typeIndex).getType(context));
            }
            break;
          }
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
          final PyType type = getType(value, context);
          return type != null ? Ref.create(type) : null;
        }
      }
      final PyType constructorType = getGenericConstructorType(function, context);
      if (constructorType != null) {
        return Ref.create(constructorType);
      }
      final String comment = function.getTypeCommentAnnotation();
      if (comment != null) {
        final PyTypeParser.ParseResult result = PyTypeParser.parsePep484FunctionTypeComment(callable, comment);
        final PyCallableType funcType = as(result.getType(), PyCallableType.class);
        if (funcType != null) {
          return Ref.create(funcType.getReturnType(context));
        }
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
        return getType(typeExpr, context);
      }
    }
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression && context.maySwitchToAST(referenceTarget)) {
      final PyTargetExpression target = (PyTargetExpression)referenceTarget;
      final String comment = getTypeComment(target);
      if (comment != null) {
        final PyType type = getStringBasedType(comment, referenceTarget, context);
        if (type instanceof PyTupleType) {
          final PyTupleExpression tupleExpr = PsiTreeUtil.getParentOfType(target, PyTupleExpression.class);
          if (tupleExpr != null) {
            return PyTypeChecker.getTargetTypeFromTupleAssignment(target, tupleExpr, (PyTupleType)type);
          }
        }
        return type;
      }
    }
    return null;
  }

  @Nullable
  private static String getTypeComment(@NotNull PyTargetExpression target) {
    final PsiElement commentContainer = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class, PyWithStatement.class,
                                                                    PyForPart.class);
    if (commentContainer != null) {
      final PsiComment comment = getSameLineTrailingCommentChild(commentContainer);
      if (comment != null) {
        final String text = comment.getText();
        return getTypeCommentValue(text);
      }
    }
    return null;
  }

  /**
   * Checks that text of a comment starts with the "type:" prefix and returns trimmed part afterwards. This trailing part is supposed to 
   * contain type annotation in PEP 484 compatible format, that can be parsed with either {@link PyTypeParser#parse(PsiElement, String)}
   * or {@link PyTypeParser#parsePep484FunctionTypeComment(PsiElement, String)}.
   */
  @Nullable
  public static String getTypeCommentValue(@NotNull String text) {
    final Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
    if (m.matches()) {
      return m.group(1);
    }
    return null;
  }

  @Nullable
  private static PsiComment getSameLineTrailingCommentChild(@NotNull PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (true) {
      if (child == null) {
        return null;
      }
      if (child instanceof PsiComment) {
        return (PsiComment)child;
      }
      if (child.getText().contains("\n")) {
        return null;
      }
      child = child.getNextSibling();
    }
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
        final List<PyType> elementTypes = new ArrayList<PyType>(genericTypes);
        if (!elementTypes.isEmpty()) {
          return new PyCollectionTypeImpl(cls, false, elementTypes);
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
            for (PsiElement resolved : tryResolving(indexExpr, context)) {
              final PyGenericType genericType = getGenericType(resolved, context);
              if (genericType != null) {
                results.add(genericType);
              }
            }
          }
        }
      }
      return results;
    }
    return Collections.emptyList();
  }

  @Nullable
  private static PyType getType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final List<PyType> members = Lists.newArrayList();
    for (PsiElement resolved : tryResolving(expression, context)) {
      members.add(getTypeForResolvedElement(resolved, context));
    }
    return PyUnionType.union(members);
  }

  @Nullable
  private static PyType getTypeForResolvedElement(@NotNull PsiElement resolved, @NotNull TypeEvalContext context) {
    final PyType unionType = getUnionType(resolved, context);
    if (unionType != null) {
      return unionType;
    }
    final Ref<PyType> optionalType = getOptionalType(resolved, context);
    if (optionalType != null) {
      return optionalType.get();
    }
    final PyType callableType = getCallableType(resolved, context);
    if (callableType != null) {
      return callableType;
    }
    final PyType parameterizedType = getParameterizedType(resolved, context);
    if (parameterizedType != null) {
      return parameterizedType;
    }
    final PyType builtinCollection = getBuiltinCollection(resolved);
    if (builtinCollection != null) {
      return builtinCollection;
    }
    final PyType genericType = getGenericType(resolved, context);
    if (genericType != null) {
      return genericType;
    }
    final Ref<PyType> classType = getClassType(resolved, context);
    if (classType != null) {
      return classType.get();
    }
    final PyType stringBasedType = getStringBasedType(resolved, context);
    if (stringBasedType != null) {
      return stringBasedType;
    }
    return null;
  }

  @Nullable
  public static PyType getType(@NotNull PsiElement resolved, @NotNull List<PyType> elementTypes) {
    final String qualifiedName = getQualifiedName(resolved);
    if ("typing.Union".equals(qualifiedName)) {
      return PyUnionType.union(elementTypes);
    }
    if ("typing.Optional".equals(qualifiedName) && elementTypes.size() == 1) {
      return PyUnionType.union(elementTypes.get(0), PyNoneType.INSTANCE);
    }
    if ("typing.Callable".equals(qualifiedName) && elementTypes.size() == 2) {
      return new PyCallableTypeImpl(null, elementTypes.get(1));
    }
    if ("typing.Tuple".equals(qualifiedName)) {
      return PyTupleType.create(resolved, elementTypes.toArray(new PyType[elementTypes.size()]));
    }
    final PyType builtinCollection = getBuiltinCollection(resolved);
    if (builtinCollection instanceof PyClassType) {
      final PyClassType classType = (PyClassType)builtinCollection;
      return new PyCollectionTypeImpl(classType.getPyClass(), false, elementTypes);
    }
    return null;
  }

  @Nullable
  public static PyType getTypeFromTargetExpression(@NotNull PyTargetExpression expression, @NotNull TypeEvalContext context) {
    // XXX: Requires switching from stub to AST
    final PyExpression assignedValue = expression.findAssignedValue();
    return assignedValue != null ? getTypeForResolvedElement(assignedValue, context) : null;
  }

  @Nullable
  private static Ref<PyType> getClassType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);
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
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getOptionalType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)element;
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context);
      if (operandNames.contains("typing.Optional")) {
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
  private static PyType getStringBasedType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyStringLiteralExpression) {
      // XXX: Requires switching from stub to AST
      final String contents = ((PyStringLiteralExpression)element).getStringValue();
      return getStringBasedType(contents, element, context);
    }
    return null;
  }

  @Nullable
  private static PyType getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull TypeEvalContext context) {
    final Project project = anchor.getProject();
    final PyExpressionCodeFragmentImpl codeFragment = new PyExpressionCodeFragmentImpl(project, "dummy.py", contents, false);
    codeFragment.setContext(anchor.getContainingFile());
    final PsiElement element = codeFragment.getFirstChild();
    if (element instanceof PyExpressionStatement) {
      final PyExpression expr = ((PyExpressionStatement)element).getExpression();
      if (expr instanceof PyTupleExpression) {
        final PyTupleExpression tupleExpr = (PyTupleExpression)expr;
        final List<PyType> elementTypes = new ArrayList<PyType>();
        for (PyExpression elementExpr : tupleExpr.getElements()) {
          elementTypes.add(getType(elementExpr, context));
        }
        return PyTupleType.create(anchor, elementTypes.toArray(new PyType[elementTypes.size()]));
      }
      return getType(expr, context);
    }
    return null;
  }

  @Nullable
  private static PyType getCallableType(@NotNull PsiElement resolved, @NotNull TypeEvalContext context) {
    if (resolved instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)resolved;
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context);
      if (operandNames.contains("typing.Callable")) {
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
  private static PyType getUnionType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)element;
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context);
      if (operandNames.contains("typing.Union")) {
        return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
      }
    }
    return null;
  }

  @Nullable
  private static PyGenericType getGenericType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyCallExpression) {
      final PyCallExpression assignedCall = (PyCallExpression)element;
      final PyExpression callee = assignedCall.getCallee();
      if (callee != null) {
        final Collection<String> calleeQNames = resolveToQualifiedNames(callee, context);
        if (calleeQNames.contains("typing.TypeVar")) {
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
    else if (indexExpr != null) {
      types.add(getType(indexExpr, context));
    }
    return types;
  }

  @Nullable
  private static PyType getParameterizedType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)element;
      final PyExpression operand = subscriptionExpr.getOperand();
      final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
      final PyType operandType = getType(operand, context);
      if (operandType instanceof PyClassType) {
        final PyClass cls = ((PyClassType)operandType).getPyClass();
        final List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
        if (PyNames.TUPLE.equals(cls.getQualifiedName())) {
          return PyTupleType.create(element, indexTypes.toArray(new PyType[indexTypes.size()]));
        }
        else if (indexExpr != null) {
          return new PyCollectionTypeImpl(cls, false, indexTypes);
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getBuiltinCollection(@NotNull PsiElement element) {
    final String collectionName = getQualifiedName(element);
    final String builtinName = COLLECTION_CLASSES.get(collectionName);
    return builtinName != null ? PyTypeParser.getTypeByName(element, builtinName) : null;
  }

  @NotNull
  private static List<PsiElement> tryResolving(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final List<PsiElement> elements = Lists.newArrayList();
    if (expression instanceof PyReferenceExpression) {
      final PyReferenceExpression referenceExpr = (PyReferenceExpression)expression;
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
      final PsiPolyVariantReference reference = referenceExpr.getReference(resolveContext);
      final List<PsiElement> resolved = PyUtil.multiResolveTopPriority(reference);
      for (PsiElement element : resolved) {
        if (element instanceof PyFunction) {
          final PyFunction function = (PyFunction)element;
          if (PyUtil.isInit(function)) {
            final PyClass cls = function.getContainingClass();
            if (cls != null) {
              elements.add(cls);
              continue;
            }
          }
        }
        else if (element instanceof PyTargetExpression) {
          final PyTargetExpression targetExpr = (PyTargetExpression)element;
          // XXX: Requires switching from stub to AST
          final PyExpression assignedValue = targetExpr.findAssignedValue();
          if (assignedValue != null) {
            elements.add(assignedValue);
            continue;
          }
        }
        if (element != null) {
          elements.add(element);
        }
      }
    }
    return !elements.isEmpty() ? elements : Collections.<PsiElement>singletonList(expression);
  }

  @NotNull
  private static Collection<String> resolveToQualifiedNames(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final Set<String> names = Sets.newLinkedHashSet();
    for (PsiElement resolved : tryResolving(expression, context)) {
      final String name = getQualifiedName(resolved);
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }

  @Nullable
  private static String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PyQualifiedNameOwner) {
      final PyQualifiedNameOwner qualifiedNameOwner = (PyQualifiedNameOwner)element;
      return qualifiedNameOwner.getQualifiedName();
    }
    return null;
  }
}
