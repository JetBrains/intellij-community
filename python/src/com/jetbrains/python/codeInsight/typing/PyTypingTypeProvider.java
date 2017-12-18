// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyParameterTypeList;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.stubs.PyClassElementType;
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.RecursionManager.doPreventingRecursion;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author vlan
 */
public class PyTypingTypeProvider extends PyTypeProviderBase {
  private static final Object RECURSION_KEY = new Object();

  public static final String TYPING = "typing";

  public static final String GENERATOR = "typing.Generator";
  public static final String ASYNC_GENERATOR = "typing.AsyncGenerator";
  public static final String COROUTINE = "typing.Coroutine";
  public static final String NAMEDTUPLE = "typing.NamedTuple";
  public static final String GENERIC = "typing.Generic";
  public static final String PROTOCOL = "typing.Protocol";
  public static final String TYPE = "typing.Type";
  public static final String ANY = "typing.Any";
  private static final String CALLABLE = "typing.Callable";
  private static final String CLASSVAR = "typing.ClassVar";

  public static final String NAMEDTUPLE_SIMPLE = "NamedTuple";
  public static final String SUPPORTS_INT_SIMPLE = "SupportsInt";
  public static final String SUPPORTS_FLOAT_SIMPLE = "SupportsFloat";
  public static final String SUPPORTS_COMPLEX_SIMPLE = "SupportsComplex";
  public static final String SUPPORTS_BYTES_SIMPLE = "SupportsBytes";
  public static final String SUPPORTS_ABS_SIMPLE = "SupportsAbs";
  public static final String SUPPORTS_ROUND_SIMPLE = "SupportsRound";

  private static final String PY2_FILE_TYPE = "typing.BinaryIO";
  private static final String PY3_BINARY_FILE_TYPE = "typing.BinaryIO";
  private static final String PY3_TEXT_FILE_TYPE = "typing.TextIO";

  public static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile("# *type: *(.*)");

  private static final ImmutableMap<String, String> COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
    .put("typing.List", "list")
    .put("typing.Dict", "dict")
    .put("typing.Set", PyNames.SET)
    .put("typing.FrozenSet", "frozenset")
    .put("typing.Tuple", PyNames.TUPLE)
    .build();

  public static final ImmutableMap<String, String> TYPING_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
    .put("list", "List")
    .put("dict", "Dict")
    .put("set", "Set")
    .put("frozenset", "FrozenSet")
    .build();

  public static final ImmutableSet<String> GENERIC_CLASSES = ImmutableSet.<String>builder()
    .add(GENERIC)
    .add("typing.AbstractGeneric")
    .add(PROTOCOL)
    .build();

  /**
   * For the following names we shouldn't go further to the RHS of assignments,
   * since they are not type aliases already and in typing.pyi are assigned to
   * some synthetic values.
   */
  private static final ImmutableSet<String> OPAQUE_NAMES = ImmutableSet.<String>builder()
    .add(PyKnownDecoratorUtil.KnownDecorator.TYPING_OVERLOAD.name())
    .add("typing.Any")
    .add("typing.TypeVar")
    .add(GENERIC)
    .add("typing.Tuple")
    .add(CALLABLE)
    .add("typing.Type")
    .add("typing.no_type_check")
    .add("typing.Union")
    .add("typing.Optional")
    .add("typing.List")
    .add("typing.Dict")
    .add("typing.DefaultDict")
    .add("typing.Set")
    .add(CLASSVAR)
    .build();

  @Nullable
  @Override
  public PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    // Check for the exact name in advance for performance reasons
    if ("Generic".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context).contains(GENERIC)) {
        return createTypingGenericType();
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Protocol".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context).contains(PROTOCOL)) {
        return createTypingProtocolType();
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final Ref<PyType> typeFromAnnotation = getParameterTypeFromAnnotation(param, context);
    if (typeFromAnnotation != null) {
      return typeFromAnnotation;
    }

    final Ref<PyType> typeFromTypeComment = getParameterTypeFromTypeComment(param, context);
    if (typeFromTypeComment != null) {
      return typeFromTypeComment;
    }

    final PyFunctionTypeAnnotation annotation = getFunctionTypeAnnotation(func);
    if (annotation != null) {
      PyParameterTypeList list = annotation.getParameterTypeList();
      List<PyExpression> paramTypes = list.getParameterTypes();
      if (paramTypes.size() == 1) {
        final PyNoneLiteralExpression noneExpr = as(paramTypes.get(0), PyNoneLiteralExpression.class);
        if (noneExpr != null && noneExpr.isEllipsis()) {
          return Ref.create();
        }
      }
      final int startOffset = omitFirstParamInTypeComment(func, annotation) ? 1 : 0;
      final List<PyParameter> funcParams = Arrays.asList(func.getParameterList().getParameters());
      final int i = funcParams.indexOf(param) - startOffset;
      if (i >= 0 && i < paramTypes.size()) {
        return getParameterTypeFromFunctionComment(paramTypes.get(i), context);
      }
    }

    final Ref<PyType> typeFromAncestors = getParameterTypeFromSupertype(param, func, context);
    if (typeFromAncestors != null) {
      return typeFromAncestors;
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getParameterTypeFromFunctionComment(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final PyStarExpression starExpr = as(expression, PyStarExpression.class);
    if (starExpr != null) {
      final PyExpression inner = starExpr.getExpression();
      if (inner != null) {
        return Ref.create(PyTypeUtil.toPositionalContainerType(expression, Ref.deref(getType(inner, new Context(context)))));
      }
    }
    final PyDoubleStarExpression doubleStarExpr = as(expression, PyDoubleStarExpression.class);
    if (doubleStarExpr != null) {
      final PyExpression inner = doubleStarExpr.getExpression();
      if (inner != null) {
        return Ref.create(PyTypeUtil.toKeywordContainerType(expression, Ref.deref(getType(inner, new Context(context)))));
      }
    }
    return getType(expression, new Context(context));
  }

  @Nullable
  private static Ref<PyType> getParameterTypeFromTypeComment(@NotNull PyNamedParameter parameter, @NotNull TypeEvalContext context) {
    final String typeComment = parameter.getTypeCommentAnnotation();

    if (typeComment != null) {
      final PyType type = Ref.deref(getStringBasedType(typeComment, parameter, context));

      if (parameter.isPositionalContainer()) {
        return Ref.create(PyTypeUtil.toPositionalContainerType(parameter, type));
      }

      if (parameter.isKeywordContainer()) {
        return Ref.create(PyTypeUtil.toKeywordContainerType(parameter, type));
      }

      return Ref.create(type);
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getParameterTypeFromAnnotation(@NotNull PyNamedParameter parameter, @NotNull TypeEvalContext context) {
    final Ref<PyType> annotationValueTypeRef = Optional
      .ofNullable(getAnnotationValue(parameter, context))
      .map(text -> getType(text, new Context(context)))
      .orElse(null);

    if (annotationValueTypeRef != null) {
      final PyType annotationValueType = annotationValueTypeRef.get();
      if (parameter.isPositionalContainer()) {
        return Ref.create(PyTypeUtil.toPositionalContainerType(parameter, annotationValueType));
      }

      if (parameter.isKeywordContainer()) {
        return Ref.create(PyTypeUtil.toKeywordContainerType(parameter, annotationValueType));
      }

      if (PyNames.NONE.equals(parameter.getDefaultValueText())) {
        return Ref.create(PyUnionType.union(annotationValueType, PyNoneType.INSTANCE));
      }

      return Ref.create(annotationValueType);
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

  @NotNull
  private static PyType createTypingGenericType() {
    return new PyCustomType(GENERIC, null, false);
  }

  @NotNull
  private static PyType createTypingProtocolType() {
    return new PyCustomType(PROTOCOL, null, false);
  }

  private static boolean omitFirstParamInTypeComment(@NotNull PyFunction func, @NotNull PyFunctionTypeAnnotation annotation) {
    return func.getContainingClass() != null && func.getModifier() != PyFunction.Modifier.STATICMETHOD &&
           annotation.getParameterTypeList().getParameterTypes().size() < func.getParameterList().getParameters().length;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      final PyFunction function = (PyFunction)callable;

      final PyExpression returnTypeAnnotation = getReturnTypeAnnotation(function, context);
      if (returnTypeAnnotation != null) {
        final Ref<PyType> typeRef = getType(returnTypeAnnotation, new Context(context));
        if (typeRef != null) {
          return Ref.create(toAsyncIfNeeded(function, typeRef.get()));
        }
      }

      final Ref<PyType> typeFromSupertype = getReturnTypeFromSupertype(function, context);
      if (typeFromSupertype != null) {
        return Ref.create(toAsyncIfNeeded(function, typeFromSupertype.get()));
      }
    }
    return null;
  }

  @Nullable
  private static PyExpression getReturnTypeAnnotation(@NotNull PyFunction function, TypeEvalContext context) {
    final PyExpression returnAnnotation = getAnnotationValue(function, context);
    if (returnAnnotation != null) {
      return returnAnnotation;
    }
    final PyFunctionTypeAnnotation functionAnnotation = getFunctionTypeAnnotation(function);
    if (functionAnnotation != null) {
      return functionAnnotation.getReturnType();
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
        final Ref<PyType> typeRef = getType(superFunctionAnnotation, new Context(context));
        if (typeRef != null) {
          return Ref.create(toAsyncIfNeeded(function, typeRef.get()));
        }
      }
    }

    return null;
  }

  @Nullable
  private static PyFunctionTypeAnnotation getFunctionTypeAnnotation(@NotNull PyFunction function) {
    final String comment = function.getTypeCommentAnnotation();
    if (comment == null) {
      return null;
    }
    final PyFunctionTypeAnnotationFile file = CachedValuesManager.getCachedValue(function, () ->
      CachedValueProvider.Result.create(new PyFunctionTypeAnnotationFile(function.getTypeCommentAnnotation(), function), function));
    return file.getAnnotation();
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    final String functionQName = function.getQualifiedName();

    if ("typing.cast".equals(functionQName)) {
      return Optional
        .ofNullable(as(callSite, PyCallExpression.class))
        .map(PyCallExpression::getArguments)
        .filter(args -> args.length > 0)
        .map(args -> getType(args[0], new Context(context)))
        .orElse(null);
    }

    if (callSite instanceof PyCallExpression && "open".equals(functionQName)) {
      return getOpenFunctionCallType(function, (PyCallExpression)callSite, LanguageLevel.forElement(callSite), context);
    }

    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      final PyTargetExpression target = (PyTargetExpression)referenceTarget;
      // Depends on typing.Generic defined as a target expression
      if (GENERIC.equals(target.getQualifiedName())) {
        return createTypingGenericType();
      }
      // Depends on typing.Protocol defined as a target expression
      if (PROTOCOL.equals(target.getQualifiedName())) {
        return createTypingProtocolType();
      }
      final Ref<PyType> annotatedType = getTypeFromTargetExpressionAnnotation(target, context);
      if (annotatedType != null) {
        return annotatedType.get();
      }

      final String name = target.getReferencedName();
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
      if (name == null || scopeOwner == null) {
        return null;
      }

      final PyClass pyClass = target.getContainingClass();

      if (target.isQualified()) {
        if (pyClass != null && scopeOwner instanceof PyFunction) {
          final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);

          boolean isInstanceAttribute = false;
          if (context.maySwitchToAST(target)) {
            isInstanceAttribute = StreamEx.of(PyUtil.multiResolveTopPriority(target.getQualifier(), resolveContext))
              .select(PyParameter.class)
              .filter(PyParameter::isSelf)
              .anyMatch(p -> PsiTreeUtil.getParentOfType(p, PyFunction.class) == scopeOwner);
          }
          else {
            isInstanceAttribute = PyUtil.isInstanceAttribute(target);
          }
          if (!isInstanceAttribute) {
            return null;
          }
          // Set isDefinition=true to start searching right from the class level.
          final PyClassTypeImpl classType = new PyClassTypeImpl(pyClass, true);
          final List<? extends RatedResolveResult> classAttrs = classType.resolveMember(name, target, AccessDirection.READ, resolveContext, true);
          if (classAttrs == null) {
            return null;
          }
          return StreamEx.of(classAttrs)
            .map(RatedResolveResult::getElement)
            .select(PyTargetExpression.class)
            .filter(x -> ScopeUtil.getScopeOwner(x) instanceof PyClass)
            .map(x -> getTypeFromTargetExpressionAnnotation(x, context))
            .nonNull()
            .map(Ref::get)
            .foldLeft(PyUnionType::union)
            .orElse(null);
        }
      }
      else {
        StreamEx<PyTargetExpression> candidates = null;
        if (context.maySwitchToAST(target)) {
          final Scope scope = ControlFlowCache.getScope(scopeOwner);
          candidates = StreamEx.of(scope.getNamedElements(name, false)).select(PyTargetExpression.class);
        }
        // Unqualified target expression in either class or module
        else if (scopeOwner instanceof PyFile) {
          candidates = StreamEx.of(((PyFile)scopeOwner).getTopLevelAttributes()).filter(t -> name.equals(t.getName()));
        }
        else if (scopeOwner instanceof PyClass) {
          candidates = StreamEx.of(((PyClass)scopeOwner).getClassAttributes()).filter(t -> name.equals(t.getName()));
        }
        if (candidates != null) {
          return candidates
            .map(x -> getTypeFromTargetExpressionAnnotation(x, context))
            .nonNull()
            .map(Ref::get)
            .findFirst()
            .orElse(null);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getTypeFromTargetExpressionAnnotation(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    final PyExpression annotation = getAnnotationValue(target, context);
    if (annotation != null) {
      return getType(annotation, new Context(context));
    }
    final String comment = target.getTypeCommentAnnotation();
    if (comment != null) {
      final Ref<PyType> fromTypeComment = getVariableTypeCommentType(comment, target, new Context(context));
      if (fromTypeComment != null) {
        final PyType type = Ref.deref(fromTypeComment);
        if (type instanceof PyTupleType) {
          final PyTupleExpression tupleExpr = PsiTreeUtil.getParentOfType(target, PyTupleExpression.class);
          if (tupleExpr != null) {
            return Ref.create(PyTypeChecker.getTargetTypeFromTupleAssignment(target, tupleExpr, (PyTupleType)type));
          }
        }
        return fromTypeComment;
      }
    }
    return null;
  }

  /**
   * Checks that text of a comment starts with the "type:" prefix and returns trimmed part afterwards. This trailing part is supposed to
   * contain type annotation in PEP 484 compatible format.
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
  @Override
  public PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final List<PyType> genericTypes = collectGenericTypes(cls, new Context(context));
    if (genericTypes.isEmpty()) {
      return null;
    }
    return new PyCollectionTypeImpl(cls, false, genericTypes);
  }

  @NotNull
  @Override
  public Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final Context ctx = new Context(context);
    if (!isGeneric(cls, ctx)) {
      return Collections.emptyMap();
    }
    final Map<PyType, PyType> results = new HashMap<>();

    for (Map.Entry<PyClass, PySubscriptionExpression> e : getResolvedSuperClassesAndTypeParameters(cls, context).entrySet()) {
      final PySubscriptionExpression subscriptionExpr = e.getValue();
      final PyClass superClass = e.getKey();
      final Map<PyType, PyType> superSubstitutions = doPreventingRecursion(RECURSION_KEY, false, () -> getGenericSubstitutions(superClass, context));
      if (superSubstitutions != null) {
        results.putAll(superSubstitutions);
      }
      if (superClass != null) {
        final List<PyType> superGenerics = collectGenericTypes(superClass, ctx);
        final List<PyExpression> indices = subscriptionExpr != null ? getSubscriptionIndices(subscriptionExpr) : Collections.emptyList();
        for (int i = 0; i < superGenerics.size(); i++) {
          final PyExpression expr = ContainerUtil.getOrElse(indices, i, null);
          final PyType superGeneric = superGenerics.get(i);
          final Ref<PyType> typeRef = expr != null ? getType(expr, ctx) : null;
          final PyType actualType = typeRef != null ? typeRef.get() : null;
          if (!superGeneric.equals(actualType)) {
            results.put(superGeneric, actualType);
          }
        }
      }
    }
    return results;
  }

  @NotNull
  private static Map<PyClass, PySubscriptionExpression> getResolvedSuperClassesAndTypeParameters(@NotNull PyClass pyClass,
                                                                                                 @NotNull TypeEvalContext context) {
    final Map<PyClass, PySubscriptionExpression> results = new LinkedHashMap<>();
    final PyClassStub classStub = pyClass.getStub();

    if (context.maySwitchToAST(pyClass)) {
      for (PyExpression e : pyClass.getSuperClassExpressions()) {
        final PySubscriptionExpression subscriptionExpr = as(e, PySubscriptionExpression.class);
        final PyExpression superExpr = subscriptionExpr != null ? subscriptionExpr.getOperand() : e;
        final PyType superType = context.getType(superExpr);
        final PyClassType superClassType = as(superType, PyClassType.class);
        final PyClass superClass = superClassType != null ? superClassType.getPyClass() : null;
        if (superClass != null) {
          results.put(superClass, subscriptionExpr);
        }
      }
      return results;
    }

    final Iterable<QualifiedName> allBaseClassesQNames;
    final List<PySubscriptionExpression> subscriptedBaseClasses = PyClassElementType.getSubscriptedSuperClassesStubLike(pyClass);
    final Map<QualifiedName, PySubscriptionExpression> baseClassQNameToExpr = new HashMap<>();
    if (classStub == null) {
      allBaseClassesQNames = PyClassElementType.getSuperClassQNames(pyClass).keySet();
    }
    else {
      allBaseClassesQNames = classStub.getSuperClasses().keySet();
    }
    for (PySubscriptionExpression subscriptedBase : subscriptedBaseClasses) {
      final PyExpression operand = subscriptedBase.getOperand();
      if (operand instanceof PyReferenceExpression) {
        final QualifiedName className = PyPsiUtils.asQualifiedName(operand);
        baseClassQNameToExpr.put(className, subscriptedBase);
      }
    }
    for (QualifiedName qName : allBaseClassesQNames) {
      final List<PsiElement> classes = PyResolveUtil.resolveQualifiedNameInFile(qName, (PyFile)pyClass.getContainingFile(), context);
      // Better way to handle results of the multiresove
      final PyClass firstFound = ContainerUtil.findInstance(classes, PyClass.class);
      if (firstFound != null) {
        results.put(firstFound, baseClassQNameToExpr.get(qName));
      }
    }
    return results;
  }

  @NotNull
  private static List<PyExpression> getSubscriptionIndices(@NotNull PySubscriptionExpression expr) {
    final PyExpression indexExpr = expr.getIndexExpression();
    final PyTupleExpression tupleExpr = as(indexExpr, PyTupleExpression.class);
    return tupleExpr != null ? Arrays.asList(tupleExpr.getElements()) : Collections.singletonList(indexExpr);
  }

  @NotNull
  private static List<PyType> collectGenericTypes(@NotNull PyClass cls, @NotNull Context context) {
    if (!isGeneric(cls, context)) {
      return Collections.emptyList();
    }
    final TypeEvalContext typeEvalContext = context.getTypeContext();
    return StreamEx.of(PyClassElementType.getSubscriptedSuperClassesStubLike(cls))
      .map(PySubscriptionExpression::getIndexExpression)
      .flatMap(e -> {
        final PyTupleExpression tupleExpr = as(e, PyTupleExpression.class);
        return tupleExpr != null ? StreamEx.of(tupleExpr.getElements()) : StreamEx.of(e);
      })
      .nonNull()
      .flatMap(e -> tryResolving(e, typeEvalContext).stream())
      .map(e -> getGenericTypeFromTypeVar(e, context))
      .select(PyType.class)
      .distinct()
      .toList();
  }

  private static boolean isGeneric(@NotNull PyClass cls, @NotNull Context context) {
    for (PyClassLikeType ancestor : cls.getAncestorTypes(context.getTypeContext())) {
      if (ancestor != null && GENERIC_CLASSES.contains(ancestor.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static Ref<PyType> getType(@NotNull PyExpression expression, @NotNull Context context) {
    final List<PyType> members = Lists.newArrayList();
    boolean foundAny = false;
    for (PsiElement resolved : tryResolving(expression, context.getTypeContext())) {
      final Ref<PyType> typeRef = getTypeForResolvedElement(resolved, context);
      if (typeRef != null) {
        final PyType type = typeRef.get();
        if (type == null) {
          foundAny = true;
        }
        members.add(type);
      }
    }
    final PyType union = PyUnionType.union(members);
    return union != null || foundAny ? Ref.create(union) : null;
  }

  @Nullable
  private static Ref<PyType> getTypeForResolvedElement(@NotNull PsiElement resolved, @NotNull Context context) {
    if (context.getExpressionCache().contains(resolved)) {
      // Recursive types are not yet supported
      return null;
    }

    context.getExpressionCache().add(resolved);
    try {
      final PyType unionType = getUnionType(resolved, context);
      if (unionType != null) {
        return Ref.create(unionType);
      }
      final Ref<PyType> optionalType = getOptionalType(resolved, context);
      if (optionalType != null) {
        return optionalType;
      }
      final PyType callableType = getCallableType(resolved, context);
      if (callableType != null) {
        return Ref.create(callableType);
      }
      final Ref<PyType> classObjType = getClassObjectType(resolved, context);
      if (classObjType != null) {
        return classObjType;
      }
      final PyType parameterizedType = getParameterizedType(resolved, context);
      if (parameterizedType != null) {
        return Ref.create(parameterizedType);
      }
      final PyType builtinCollection = getBuiltinCollection(resolved, context.getTypeContext());
      if (builtinCollection != null) {
        return Ref.create(builtinCollection);
      }
      final PyType genericType = getGenericTypeFromTypeVar(resolved, context);
      if (genericType != null) {
        return Ref.create(genericType);
      }
      final PyType stringBasedType = getStringLiteralType(resolved, context);
      if (stringBasedType != null) {
        return Ref.create(stringBasedType);
      }
      final Ref<PyType> anyType = getAnyType(resolved);
      if (anyType != null) {
        return anyType;
      }
      final Ref<PyType> classType = getClassType(resolved, context.getTypeContext());
      if (classType != null) {
        return classType;
      }
      return null;
    }
    finally {
      context.getExpressionCache().remove(resolved);
    }
  }

  @Nullable
  private static Ref<PyType> getClassObjectType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subsExpr = (PySubscriptionExpression)resolved;
      final PyExpression operand = subsExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(TYPE)) {
        final PyExpression indexExpr = subsExpr.getIndexExpression();
        if (indexExpr != null) {
          if (resolveToQualifiedNames(indexExpr, context.getTypeContext()).contains(ANY)) {
            return Ref.create(PyBuiltinCache.getInstance(resolved).getTypeType());
          }
          final PyType type = Ref.deref(getType(indexExpr, context));
          final PyClassType classType = as(type, PyClassType.class);
          if (classType != null && !classType.isDefinition()) {
            return Ref.create(new PyClassTypeImpl(classType.getPyClass(), true));
          }
          final PyGenericType typeVar = as(type, PyGenericType.class);
          if (typeVar != null && !typeVar.isDefinition()) {
            return Ref.create(new PyGenericType(typeVar.getName(), typeVar.getBound(), true));
          }
          // Represent Type[Union[str, int]] internally as Union[Type[str], Type[int]]
          final PyUnionType unionType = as(type, PyUnionType.class);
          if (unionType != null &&
              unionType.getMembers().stream().allMatch(t -> t instanceof PyClassType && !((PyClassType)t).isDefinition())) {
            return Ref.create(PyUnionType.union(ContainerUtil.map(unionType.getMembers(), t -> ((PyClassType)t).toClass())));
          }
        }
        // Map Type[Something] with unsupported type parameter to Any, instead of generic type for the class "type"
        return Ref.create();
      }
    }
    // Replace plain non-parametrized Type with its builtin counterpart
    else if (TYPE.equals(getQualifiedName(resolved))) {
      return Ref.create(PyBuiltinCache.getInstance(resolved).getTypeType());
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getAnyType(@NotNull PsiElement element) {
    return ANY.equals(getQualifiedName(element)) ? Ref.create() : null;
  }

  @Nullable
  private static Ref<PyType> getClassType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);
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
  private static Ref<PyType> getOptionalType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)element;
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains("typing.Optional")) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr != null) {
          final PyType type = Ref.deref(getType(indexExpr, context));
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
  private static PyExpression getAnnotationValue(@NotNull PyAnnotationOwner owner, @NotNull TypeEvalContext context) {
    if (context.maySwitchToAST(owner)) {
      final PyAnnotation annotation = owner.getAnnotation();
      if (annotation != null) {
        return annotation.getValue();
      }
    }
    else {
      final String annotationText = owner.getAnnotationValue();
      if (annotationText != null) {
        return PyUtil.createExpressionFromFragment(annotationText, owner.getContainingFile());
      }
    }
    return null;
  }

  @Nullable
  public static Ref<PyType> getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull TypeEvalContext context) {
    return getStringBasedType(contents, anchor, new Context(context));
  }

  @Nullable
  private static Ref<PyType> getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull Context context) {
    final PsiFile file = FileContextUtil.getContextFile(anchor);
    if (file == null) {
      return null;
    }
    final PyExpression expr = PyUtil.createExpressionFromFragment(contents, file);
    return expr != null ? getType(expr, context) : null;
  }

  @Nullable
  private static PyType getStringLiteralType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyStringLiteralExpression) {
      final String contents = ((PyStringLiteralExpression)element).getStringValue();
      return Ref.deref(getStringBasedType(contents, element, context));
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getVariableTypeCommentType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull Context context) {
    // TODO pass the real anchor as the context element for the fragment to resolve local classes/type aliases
    final PyExpression expr = PyUtil.createExpressionFromFragment(contents, anchor.getContainingFile());
    if (expr != null) {
      // Such syntax is specific to "# type:" comments, unpacking in type hints is not allowed anywhere else
      if (expr instanceof PyTupleExpression) {
        final PyTupleExpression tupleExpr = (PyTupleExpression)expr;
        final List<PyType> elementTypes = ContainerUtil.map(tupleExpr.getElements(), elementExpr -> Ref.deref(getType(elementExpr, context)));
        return Ref.create(PyTupleType.create(anchor, elementTypes));
      }
      return getType(expr, context);
    }
    return null;
  }

  @Nullable
  private static PyType getCallableType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)resolved;
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(CALLABLE)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr instanceof PyTupleExpression) {
          final PyTupleExpression tupleExpr = (PyTupleExpression)indexExpr;
          final PyExpression[] elements = tupleExpr.getElements();
          if (elements.length == 2) {
            final PyExpression parametersExpr = elements[0];
            final PyExpression returnTypeExpr = elements[1];
            if (parametersExpr instanceof PyListLiteralExpression) {
              final List<PyCallableParameter> parameters = new ArrayList<>();
              final PyListLiteralExpression listExpr = (PyListLiteralExpression)parametersExpr;
              for (PyExpression argExpr : listExpr.getElements()) {
                parameters.add(PyCallableParameterImpl.nonPsi(Ref.deref(getType(argExpr, context))));
              }
              final PyType returnType = Ref.deref(getType(returnTypeExpr, context));
              return new PyCallableTypeImpl(parameters, returnType);
            }
            if (isEllipsis(parametersExpr)) {
              return new PyCallableTypeImpl(null, Ref.deref(getType(returnTypeExpr, context)));
            }
          }
        }
      }
    }
    else if (resolved instanceof PyTargetExpression) {
      if (resolveToQualifiedNames((PyTargetExpression)resolved, context.getTypeContext()).contains(CALLABLE)) {
        return new PyCallableTypeImpl(null, null);
      }
    }
    return null;
  }

  private static boolean isEllipsis(@NotNull PyExpression parametersExpr) {
    return parametersExpr instanceof PyNoneLiteralExpression && ((PyNoneLiteralExpression)parametersExpr).isEllipsis();
  }

  @Nullable
  private static PyType getUnionType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)element;
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains("typing.Union")) {
        return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
      }
    }
    return null;
  }

  @Nullable
  private static PyGenericType getGenericTypeFromTypeVar(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyCallExpression) {
      final PyCallExpression assignedCall = (PyCallExpression)element;
      final PyExpression callee = assignedCall.getCallee();
      if (callee != null) {
        final Collection<String> calleeQNames = resolveToQualifiedNames(callee, context.getTypeContext());
        if (calleeQNames.contains("typing.TypeVar")) {
          final PyExpression[] arguments = assignedCall.getArguments();
          if (arguments.length > 0) {
            final PyExpression firstArgument = arguments[0];
            if (firstArgument instanceof PyStringLiteralExpression) {
              final String name = ((PyStringLiteralExpression)firstArgument).getStringValue();
              return new PyGenericType(name, getGenericTypeBound(arguments, context));
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getGenericTypeBound(@NotNull PyExpression[] typeVarArguments, @NotNull Context context) {
    final List<PyType> types = new ArrayList<>();
    for (int i = 1; i < typeVarArguments.length; i++) {
      types.add(Ref.deref(getType(typeVarArguments[i], context)));
    }
    return PyUnionType.union(types);
  }

  @NotNull
  private static List<PyType> getIndexTypes(@NotNull PySubscriptionExpression expression, @NotNull Context context) {
    final List<PyType> types = new ArrayList<>();
    final PyExpression indexExpr = expression.getIndexExpression();
    if (indexExpr instanceof PyTupleExpression) {
      final PyTupleExpression tupleExpr = (PyTupleExpression)indexExpr;
      for (PyExpression expr : tupleExpr.getElements()) {
        types.add(Ref.deref(getType(expr, context)));
      }
    }
    else if (indexExpr != null) {
      types.add(Ref.deref(getType(indexExpr, context)));
    }
    return types;
  }

  @Nullable
  private static PyType getParameterizedType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)element;
      final PyExpression operand = subscriptionExpr.getOperand();
      final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
      final PyType operandType = Ref.deref(getType(operand, context));
      if (operandType instanceof PyClassType) {
        final PyClass cls = ((PyClassType)operandType).getPyClass();
        final List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
        if (PyNames.TUPLE.equals(cls.getQualifiedName())) {
          if (indexExpr instanceof PyTupleExpression) {
            final PyExpression[] elements = ((PyTupleExpression)indexExpr).getElements();
            if (elements.length == 2 && isEllipsis(elements[1])) {
              return PyTupleType.createHomogeneous(element, indexTypes.get(0));
            }
          }
          return PyTupleType.create(element, indexTypes);
        }
        else if (indexExpr != null) {
          return new PyCollectionTypeImpl(cls, false, indexTypes);
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getBuiltinCollection(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final String collectionName = getQualifiedName(element);
    final String builtinName = COLLECTION_CLASSES.get(collectionName);
    return builtinName != null ? PyTypeParser.getTypeByName(element, builtinName, context) : null;
  }

  @NotNull
  private static List<PsiElement> tryResolving(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final List<PsiElement> elements = Lists.newArrayList();
    if (expression instanceof PyReferenceExpression) {
      final List<PsiElement> results;
      if (context.maySwitchToAST(expression)) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
        results = PyUtil.multiResolveTopPriority(expression, resolveContext);
      }
      else {
        results = tryResolvingOnStubs((PyReferenceExpression)expression, context);
      }
      for (PsiElement element : results) {
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
        final String name = element != null ? getQualifiedName(element) : null;
        if (name != null && OPAQUE_NAMES.contains(name)) {
          elements.add(element);
          continue;
        }
        // Presumably, a TypeVar definition or a type alias
        if (element instanceof PyTargetExpression) {
          final PyTargetExpression targetExpr = (PyTargetExpression)element;
          final PyExpression assignedValue;
          if (context.maySwitchToAST(targetExpr)) {
            assignedValue = targetExpr.findAssignedValue();
          }
          else {
            assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(targetExpr);
          }
          if (assignedValue != null) {
            elements.add(assignedValue);
            continue;
          }
        }
        if (isBuiltinPathLike(element)) {
          // see https://github.com/python/typeshed/commit/41561f11c7b06368aebe512acf69d8010662266d
          // or comment in typeshed/stdlib/3/builtins.pyi near _PathLike class
          final QualifiedName osPathLikeQName = QualifiedName.fromComponents("os", PyNames.PATH_LIKE);
          final PsiElement osPathLike = PyResolveImportUtil.resolveTopLevelMember(osPathLikeQName, PyResolveImportUtil.fromFoothold(element));
          if (osPathLike != null) {
            elements.add(osPathLike);
            continue;
          }
        }
        if (element != null) {
          elements.add(element);
        }
      }
    }
    return !elements.isEmpty() ? elements : Collections.singletonList(expression);
  }

  @NotNull
  private static List<PsiElement> tryResolvingOnStubs(@NotNull PyReferenceExpression expression,
                                                      @NotNull TypeEvalContext context) {
    
    final QualifiedName qualifiedName = expression.asQualifiedName();
    final PyFile pyFile = as(FileContextUtil.getContextFile(expression), PyFile.class);

    if (pyFile != null && qualifiedName != null) {
      return PyResolveUtil.resolveQualifiedNameInFile(qualifiedName, pyFile, context);
    }
    return Collections.singletonList(expression);
  }

  private static boolean isBuiltinPathLike(@Nullable PsiElement element) {
    return element instanceof PyClass &&
           PyBuiltinCache.getInstance(element).isBuiltin(element) &&
           ("_" + PyNames.PATH_LIKE).equals(((PyClass)element).getName());
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

  @Nullable
  public static PyType toAsyncIfNeeded(@NotNull PyFunction function, @Nullable PyType returnType) {
    if (function.isAsync() && function.isAsyncAllowed()) {
      if (!function.isGenerator()) {
        return wrapInCoroutineType(returnType, function);
      }
      else if (returnType instanceof PyClassLikeType &&
               returnType instanceof PyCollectionType &&
               GENERATOR.equals(((PyClassLikeType)returnType).getClassQName())) {
        return wrapInAsyncGeneratorType(((PyCollectionType)returnType).getIteratedItemType(), function);
      }
    }

    return returnType;
  }

  @Nullable
  private static PyType wrapInCoroutineType(@Nullable PyType returnType, @NotNull PsiElement resolveAnchor) {
    final PyClass coroutine = as(PyResolveImportUtil.resolveTopLevelMember(QualifiedName.fromDottedString(COROUTINE),
                                                                           PyResolveImportUtil.fromFoothold(resolveAnchor)), PyClass.class);
    return coroutine != null ? new PyCollectionTypeImpl(coroutine, false, Arrays.asList(null, null, returnType)) : null;
  }

  @Nullable
  public static PyType wrapInGeneratorType(@Nullable PyType elementType, @Nullable PyType returnType, @NotNull PsiElement anchor) {
    final PyClass generator = as(PyResolveImportUtil.resolveTopLevelMember(QualifiedName.fromDottedString(GENERATOR),
                                                                           PyResolveImportUtil.fromFoothold(anchor)), PyClass.class);
    return generator != null ? new PyCollectionTypeImpl(generator, false, Arrays.asList(elementType, null, returnType)) : null;
  }

  @Nullable
  private static PyType wrapInAsyncGeneratorType(@Nullable PyType elementType, @NotNull PsiElement anchor) {
    final PyClass asyncGenerator = as(PyResolveImportUtil.resolveTopLevelMember(QualifiedName.fromDottedString(ASYNC_GENERATOR),
                                                                                PyResolveImportUtil.fromFoothold(anchor)), PyClass.class);
    return asyncGenerator != null ? new PyCollectionTypeImpl(asyncGenerator, false, Arrays.asList(elementType, null)) : null;
  }

  /**
   * @deprecated Use {@link PyTypingTypeProvider#coroutineOrGeneratorElementType(PyType)} instead.
   * This method will be removed in 2018.2.
   */
  @Nullable
  @Deprecated
  public static Ref<PyType> coroutineOrGeneratorElementType(@Nullable PyType coroutineOrGeneratorType, @NotNull TypeEvalContext context) {
    return coroutineOrGeneratorElementType(coroutineOrGeneratorType);
  }

  @Nullable
  public static Ref<PyType> coroutineOrGeneratorElementType(@Nullable PyType coroutineOrGeneratorType) {
    final PyCollectionType genericType = as(coroutineOrGeneratorType, PyCollectionType.class);
    final PyClassType classType = as(coroutineOrGeneratorType, PyClassType.class);

    if (genericType != null && classType != null && ArrayUtil.contains(classType.getClassQName(), COROUTINE, GENERATOR)) {
      return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 2, null));
    }

    return null;
  }

  @NotNull
  public static Ref<PyType> getOpenFunctionCallType(@NotNull PyFunction function,
                                                    @NotNull PyCallExpression call,
                                                    @NotNull LanguageLevel typeLevel,
                                                    @NotNull TypeEvalContext context) {
    final String type =
      typeLevel.isPython2()
      ? PY2_FILE_TYPE
      : getOpenMode(function, call, context).contains("b")
        ? PY3_BINARY_FILE_TYPE
        : PY3_TEXT_FILE_TYPE;

    return Ref.create(PyTypeParser.getTypeByName(call, type, context));
  }

  public static boolean isClassVarAnnotation(@NotNull PyAnnotation annotation, @NotNull TypeEvalContext context) {
    final PyExpression value = annotation.getValue();

    if (value instanceof PySubscriptionExpression) {
      final PyExpression operand = ((PySubscriptionExpression)value).getOperand();
      return operand instanceof PyReferenceExpression && resolveToQualifiedNames(operand, context).contains(CLASSVAR);
    }
    else if (value instanceof PyReferenceExpression) {
      return resolveToQualifiedNames(value, context).contains(CLASSVAR);
    }

    return false;
  }

  @NotNull
  private static String getOpenMode(@NotNull PyFunction function, @NotNull PyCallExpression call, @NotNull TypeEvalContext context) {
    final Map<PyExpression, PyCallableParameter> arguments =
      PyCallExpressionHelper.mapArguments(call, function, context).getMappedParameters();

    for (Map.Entry<PyExpression, PyCallableParameter> entry : arguments.entrySet()) {
      if ("mode".equals(entry.getValue().getName())) {
        PyExpression argument = entry.getKey();
        if (argument instanceof PyKeywordArgument) {
          argument = ((PyKeywordArgument)argument).getValueExpression();
        }
        if (argument instanceof PyStringLiteralExpression) {
          return ((PyStringLiteralExpression)argument).getStringValue();
        }
        break;
      }
    }

    return "r";
  }

  private static class Context {
    @NotNull private final TypeEvalContext myContext;
    @NotNull private final Set<PsiElement> myCache = new HashSet<>();

    private Context(@NotNull TypeEvalContext context) {
      myContext = context;
    }

    @NotNull
    public TypeEvalContext getTypeContext() {
      return myContext;
    }
    
    @NotNull
    public Set<PsiElement> getExpressionCache() {
      return myCache;
    }
  }
}
