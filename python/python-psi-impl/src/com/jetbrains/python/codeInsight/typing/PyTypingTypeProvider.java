// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
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
import static com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL;
import static com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL_EXT;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author vlan
 */
public class PyTypingTypeProvider extends PyTypeProviderBase {

  public static final String TYPING = "typing";

  public static final String GENERATOR = "typing.Generator";
  public static final String ASYNC_GENERATOR = "typing.AsyncGenerator";
  public static final String COROUTINE = "typing.Coroutine";
  public static final String NAMEDTUPLE = "typing.NamedTuple";
  public static final String TYPED_DICT = "typing.TypedDict";
  public static final String TYPED_DICT_EXT = "typing_extensions.TypedDict";
  public static final String GENERIC = "typing.Generic";
  public static final String PROTOCOL = "typing.Protocol";
  public static final String PROTOCOL_EXT = "typing_extensions.Protocol";
  public static final String TYPE = "typing.Type";
  public static final String ANY = "typing.Any";
  public static final String NEW_TYPE = "typing.NewType";
  public static final String CALLABLE = "typing.Callable";
  public static final String MAPPING = "typing.Mapping";
  public static final String MAPPING_GET = "typing.Mapping.get";
  private static final String LIST = "typing.List";
  private static final String DICT = "typing.Dict";
  private static final String DEFAULT_DICT = "typing.DefaultDict";
  private static final String ORDERED_DICT = "typing.OrderedDict";
  private static final String SET = "typing.Set";
  private static final String FROZEN_SET = "typing.FrozenSet";
  private static final String COUNTER = "typing.Counter";
  private static final String DEQUE = "typing.Deque";
  private static final String TUPLE = "typing.Tuple";
  public static final String CLASS_VAR = "typing.ClassVar";
  public static final String TYPE_VAR = "typing.TypeVar";
  private static final String CHAIN_MAP = "typing.ChainMap";
  public static final String UNION = "typing.Union";
  public static final String OPTIONAL = "typing.Optional";
  public static final String NO_RETURN = "typing.NoReturn";
  public static final String FINAL = "typing.Final";
  public static final String FINAL_EXT = "typing_extensions.Final";
  public static final String LITERAL = "typing.Literal";
  public static final String LITERAL_EXT = "typing_extensions.Literal";

  private static final String PY2_FILE_TYPE = "typing.BinaryIO";
  private static final String PY3_BINARY_FILE_TYPE = "typing.BinaryIO";
  private static final String PY3_TEXT_FILE_TYPE = "typing.TextIO";

  private static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile("# *type: *([^#]+) *(#.*)?");
  public static final String IGNORE = "ignore";

  public static final ImmutableMap<String, String> BUILTIN_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
    .put(LIST, "list")
    .put(DICT, "dict")
    .put(SET, PyNames.SET)
    .put(FROZEN_SET, "frozenset")
    .put(TUPLE, PyNames.TUPLE)
    .build();

  private static final ImmutableMap<String, String> COLLECTIONS_CLASSES = ImmutableMap.<String, String>builder()
    .put(DEFAULT_DICT, "collections.defaultdict")
    .put(ORDERED_DICT, "collections.OrderedDict")
    .put(COUNTER, "collections.Counter")
    .put(DEQUE, "collections.deque")
    .put(CHAIN_MAP, "collections.ChainMap")
    .build();

  public static final ImmutableMap<String, String> TYPING_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
    .put("list", "List")
    .put("dict", "Dict")
    .put("set", "Set")
    .put("frozenset", "FrozenSet")
    .build();

  public static final ImmutableSet<String> GENERIC_CLASSES = ImmutableSet.<String>builder()
    // special forms
    .add(TUPLE, GENERIC, PROTOCOL, CALLABLE, TYPE, CLASS_VAR, FINAL, LITERAL)
    // type aliases
    .add(UNION, OPTIONAL, LIST, DICT, DEFAULT_DICT, ORDERED_DICT, SET, FROZEN_SET, COUNTER, DEQUE, CHAIN_MAP)
    .add(PROTOCOL_EXT, FINAL_EXT, LITERAL_EXT)
    .build();

  /**
   * For the following names we shouldn't go further to the RHS of assignments,
   * since they are not type aliases already and in typing.pyi are assigned to
   * some synthetic values.
   */
  public static final ImmutableSet<String> OPAQUE_NAMES = ImmutableSet.<String>builder()
    .add(PyKnownDecoratorUtil.KnownDecorator.TYPING_OVERLOAD.name())
    .add(ANY)
    .add(TYPE_VAR)
    .add(GENERIC)
    .add(TUPLE)
    .add(CALLABLE)
    .add(TYPE)
    .add("typing.no_type_check")
    .add(UNION)
    .add(OPTIONAL)
    .add(LIST)
    .add(DICT)
    .add(DEFAULT_DICT)
    .add(ORDERED_DICT)
    .add(SET)
    .add(FROZEN_SET)
    .add(PROTOCOL, PROTOCOL_EXT)
    .add(CLASS_VAR)
    .add(COUNTER)
    .add(DEQUE)
    .add(CHAIN_MAP)
    .add(NO_RETURN)
    .add(FINAL, FINAL_EXT)
    .add(LITERAL, LITERAL_EXT)
    .add(TYPED_DICT, TYPED_DICT_EXT)
    .build();

  @Nullable
  @Override
  public PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    // Check for the exact name in advance for performance reasons
    if ("Generic".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context).contains(GENERIC)) {
        return createTypingGenericType(referenceExpression);
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Protocol".equals(referenceExpression.getName())) {
      if (ContainerUtil.exists(resolveToQualifiedNames(referenceExpression, context), n -> PROTOCOL.equals(n) || PROTOCOL_EXT.equals(n))) {
        return createTypingProtocolType(referenceExpression);
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Callable".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context).contains(CALLABLE)) {
        return createTypingCallableType(referenceExpression);
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

    return null;
  }

  public static boolean isGenerator(@NotNull final PyType type) {
    return type instanceof PyCollectionType && GENERATOR.equals(((PyClassLikeType)type).getClassQName());
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

  @NotNull
  private static PyType createTypingGenericType(@NotNull PsiElement anchor) {
    return new PyCustomType(GENERIC, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
  }

  @NotNull
  private static PyType createTypingProtocolType(@NotNull PsiElement anchor) {
    return new PyCustomType(PROTOCOL, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
  }

  @NotNull
  public static PyType createTypingCallableType(@NotNull PsiElement anchor) {
    return new PyCustomType(CALLABLE, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
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
    }
    return null;
  }

  @Nullable
  public static PyExpression getReturnTypeAnnotation(@NotNull PyFunction function, TypeEvalContext context) {
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

  @Nullable
  public static PyFunctionTypeAnnotation getFunctionTypeAnnotation(@NotNull PyFunction function) {
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
  public Ref<PyType> getCallType(@NotNull PyFunction function, @NotNull PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    final String functionQName = function.getQualifiedName();

    if ("typing.cast".equals(functionQName)) {
      return Optional
        .ofNullable(as(callSite, PyCallExpression.class))
        .map(PyCallExpression::getArguments)
        .filter(args -> args.length > 0)
        .map(args -> getType(args[0], new Context(context)))
        .orElse(null);
    }

    if (callSite instanceof PyCallExpression) {
      final LanguageLevel level = "open".equals(functionQName)
                                  ? LanguageLevel.forElement(callSite)
                                  : "pathlib.Path.open".equals(functionQName) || "_io.open".equals(functionQName)
                                    ? LanguageLevel.PYTHON34
                                    : null;

      if (level != null) {
        return getOpenFunctionCallType(function, (PyCallExpression)callSite, level, context);
      }
    }

    final PyClass initializedClass = PyUtil.turnConstructorIntoClass(function);
    if (initializedClass != null && TYPE_VAR.equals(initializedClass.getQualifiedName())) {
      // `typing.TypeVar` call should be assigned to a target and hence should be processed by [getReferenceType]
      // but the corresponding type is also returned here to suppress type checker on `T = TypeVar("T")` assignment.
      return Ref.create(getGenericTypeFromTypeVar(callSite, new Context(context)));
    }

    return null;
  }

  @Nullable
  private static PyType getTypedDictTypeForTarget(@NotNull PyTargetExpression referenceTarget, @NotNull TypeEvalContext context) {
    if (PyTypedDictTypeProvider.Companion.isTypedDict(referenceTarget, context)) {
      return new PyCustomType(TYPED_DICT, null, false, true,
                              PyBuiltinCache.getInstance(referenceTarget).getDictType());
    }

    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      final PyTargetExpression target = (PyTargetExpression)referenceTarget;
      final String targetQName = target.getQualifiedName();

      // Depends on typing.Generic defined as a target expression
      if (GENERIC.equals(targetQName)) {
        return Ref.create(createTypingGenericType(target));
      }
      // Depends on typing.Protocol defined as a target expression
      if (PROTOCOL.equals(targetQName) || PROTOCOL_EXT.equals(targetQName)) {
        return Ref.create(createTypingProtocolType(target));
      }
      // Depends on typing.Callable defined as a target expression
      if (CALLABLE.equals(targetQName)) {
        return Ref.create(createTypingCallableType(referenceTarget));
      }

      final PyType collection = getCollection(target, context);
      if (collection instanceof PyInstantiableType) {
        return Ref.create(((PyInstantiableType)collection).toClass());
      }

      final PyType typedDictType = getTypedDictTypeForTarget(target, context);
      if (typedDictType != null) {
        return Ref.create(typedDictType);
      }

      final Ref<PyType> annotatedType = getTypeFromTargetExpressionAnnotation(target, context);
      if (annotatedType != null) {
        return annotatedType;
      }

      final PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(target);
      if (assignedValue != null) {
        final PyType type = getGenericTypeFromTypeVar(assignedValue, new Context(context));
        if (type != null) {
          return Ref.create(type);
        }
      }

      final String name = target.getReferencedName();
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
      if (name == null || scopeOwner == null) {
        return null;
      }

      final PyClass pyClass = target.getContainingClass();

      if (target.isQualified()) {
        if (pyClass != null && scopeOwner instanceof PyFunction) {
          final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);

          boolean isInstanceAttribute;
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
          final List<? extends RatedResolveResult> classAttrs =
            classType.resolveMember(name, target, AccessDirection.READ, resolveContext, true);
          if (classAttrs == null) {
            return null;
          }
          return StreamEx.of(classAttrs)
            .map(RatedResolveResult::getElement)
            .select(PyTargetExpression.class)
            .filter(x -> ScopeUtil.getScopeOwner(x) instanceof PyClass)
            .map(x -> getTypeFromTargetExpressionAnnotation(x, context))
            .collect(PyTypeUtil.toUnionFromRef());
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
      return getVariableTypeCommentType(comment, target, new Context(context));
    }
    return null;
  }

  /**
   * Checks that text of a comment starts with "# type:" prefix and returns trimmed type hint after it.
   * The trailing part is supposed to contain type annotation in PEP 484 compatible format and an optional
   * plain text comment separated from it with another "#".
   * <p>
   * For instance, for {@code # type: List[int]  # comment} it returns {@code List[int]}.
   * <p>
   * This method cannot return an empty string.
   *
   * @see #getTypeCommentValueRange(String)
   */
  @Nullable
  public static String getTypeCommentValue(@NotNull String text) {
    final Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
    if (m.matches()) {
      return StringUtil.nullize(m.group(1).trim());
    }
    return null;
  }

  /**
   * Returns the corresponding text range for a type hint as returned by {@link #getTypeCommentValue(String)}.
   *
   * @see #getTypeCommentValue(String)
   */
  @Nullable
  public static TextRange getTypeCommentValueRange(@NotNull String text) {
    final Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
    if (m.matches()) {
      final String hint = getTypeCommentValue(text);
      if (hint != null) {
        return TextRange.from(m.start(1), hint.length());
      }
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
    if (!isGeneric(cls, context)) {
      return Collections.emptyMap();
    }
    final Map<PyType, PyType> results = new HashMap<>();

    for (Map.Entry<PyClass, PySubscriptionExpression> e : getResolvedSuperClassesAndTypeParameters(cls, context).entrySet()) {
      final PySubscriptionExpression subscriptionExpr = e.getValue();
      final PyClass superClass = e.getKey();
      final Map<PyType, PyType> superSubstitutions =
        doPreventingRecursion(superClass, false, () -> getGenericSubstitutions(superClass, context));
      if (superSubstitutions != null) {
        results.putAll(superSubstitutions);
      }
      final Context ctx = new Context(context);
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
      if (qName == null) continue;
      final List<PsiElement> classes = PyResolveUtil.resolveQualifiedNameInScope(qName, (PyFile)pyClass.getContainingFile(), context);
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
    if (!isGeneric(cls, context.getTypeContext())) {
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

  public static boolean isGeneric(@NotNull PyWithAncestors descendant, @NotNull TypeEvalContext context) {
    for (PyClassLikeType ancestor : descendant.getAncestorTypes(context)) {
      if (ancestor != null && GENERIC_CLASSES.contains(ancestor.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static Ref<PyType> getType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    return getType(expression, new Context(context));
  }

  @Nullable
  static Ref<PyType> getType(@NotNull PyExpression expression, @NotNull Context context) {
    final List<PyType> members = new ArrayList<>();
    boolean foundAny = false;
    for (Pair<PyTargetExpression, PsiElement> pair : tryResolvingWithAliases(expression, context.getTypeContext())) {
      final Ref<PyType> typeRef = getTypeForResolvedElement(pair.getFirst(), pair.getSecond(), context);
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
  private static Ref<PyType> getTypeForResolvedElement(@Nullable PyTargetExpression alias,
                                                       @NotNull PsiElement resolved,
                                                       @NotNull Context context) {
    if (alias != null) {
      if (context.getExpressionCache().contains(alias)) {
        // Recursive types are not yet supported
        return null;
      }
      context.getExpressionCache().add(alias);
    }
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
        return Ref.create(addTypeVarAlias(classObjType.get(), alias));
      }
      final Ref<PyType> finalType = getFinalType(resolved, context);
      if (finalType != null) {
        return finalType;
      }
      final Ref<PyType> literalType = getLiteralType(resolved, context);
      if (literalType != null) {
        return literalType;
      }
      final PyType parameterizedType = getParameterizedType(resolved, context);
      if (parameterizedType != null) {
        return Ref.create(parameterizedType);
      }
      final PyType collection = getCollection(resolved, context.getTypeContext());
      if (collection != null) {
        return Ref.create(collection);
      }
      final PyType genericType = getGenericTypeFromTypeVar(resolved, context);
      if (genericType != null) {
        return Ref.create(addTypeVarAlias(genericType, alias));
      }
      final PyType stringBasedType = getStringLiteralType(resolved, context);
      if (stringBasedType != null) {
        return Ref.create(stringBasedType);
      }
      final Ref<PyType> anyType = getAnyType(resolved);
      if (anyType != null) {
        return anyType;
      }
      // We perform chained resolve only for actual aliases as tryResolvingWithAliases() returns the passed-in
      // expression both when it's not a reference expression and when it's failed to resolve it, hence we might
      // hit SOE for mere unresolved references in the latter case.
      if (alias != null) {
        final Ref<PyType> aliasedType = getAliasedType(resolved, context);
        if (aliasedType != null) {
          return aliasedType;
        }
      }
      final PyType typedDictType = PyTypedDictTypeProvider.Companion.getTypedDictTypeForResolvedElement(resolved, context.getTypeContext());
      if (typedDictType != null) {
        return Ref.create(typedDictType);
      }
      final Ref<PyType> classType = getClassType(resolved, context.getTypeContext());
      if (classType != null) {
        return classType;
      }
      return null;
    }
    finally {
      if (alias != null) {
        context.getExpressionCache().remove(alias);
      }
    }
  }

  @Nullable
  private static Ref<PyType> getAliasedType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyReferenceExpression && ((PyReferenceExpression)resolved).asQualifiedName() != null) {
      return getType((PyExpression)resolved, context);
    }
    return null;
  }

  @Nullable
  private static PyType addTypeVarAlias(@Nullable PyType type, @Nullable PyTargetExpression alias) {
    final PyGenericType typeVar = as(type, PyGenericType.class);
    if (typeVar != null) {
      return new PyGenericType(typeVar.getName(), typeVar.getBound(), typeVar.isDefinition(), alias);
    }
    return type;
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
      if (operandNames.contains(OPTIONAL)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr != null) {
          final Ref<PyType> typeRef = getType(indexExpr, context);
          if (typeRef != null) {
            return Ref.create(PyUnionType.union(typeRef.get(), PyNoneType.INSTANCE));
          }
        }
        return Ref.create();
      }
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getLiteralType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)resolved;

      final Collection<String> operandNames = resolveToQualifiedNames(subscriptionExpr.getOperand(), context.getTypeContext());
      if (ContainerUtil.exists(operandNames, name -> name.equals(LITERAL) || name.equals(LITERAL_EXT))) {
        return Optional
          .ofNullable(subscriptionExpr.getIndexExpression())
          .map(index -> PyLiteralType.Companion.fromLiteralParameter(index, context.getTypeContext()))
          .map(Ref::create)
          .orElse(null);
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getFinalType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)resolved;

      if (eventuallyResolvesToFinal(subscriptionExpr.getOperand(), context.getTypeContext())) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr != null) {
          return getType(indexExpr, context);
        }
      }
    }

    return null;
  }

  public static boolean isFinal(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    return ContainerUtil.exists(PyKnownDecoratorUtil.getKnownDecorators(decoratable, context),
                                d -> d == TYPING_FINAL || d == TYPING_FINAL_EXT);
  }

  public static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean isFinal(@NotNull T owner, @NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(owner, context, p -> isFinalImpl(owner, p));
  }

  private static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean isFinalImpl(@NotNull T owner,
                                                                                        @NotNull TypeEvalContext context) {
    final PyExpression annotation = getAnnotationValue(owner, context);
    if (annotation instanceof PySubscriptionExpression) {
      return eventuallyResolvesToFinal(((PySubscriptionExpression)annotation).getOperand(), context);
    }
    else if (annotation instanceof PyReferenceExpression) {
      return eventuallyResolvesToFinal(annotation, context);
    }

    final String typeCommentValue = owner.getTypeCommentAnnotation();
    final PyExpression typeComment = typeCommentValue == null ? null : toExpression(typeCommentValue, owner);
    if (typeComment instanceof PySubscriptionExpression) {
      return eventuallyResolvesToFinal(((PySubscriptionExpression)typeComment).getOperand(), context);
    }
    else if (typeComment instanceof PyReferenceExpression) {
      return eventuallyResolvesToFinal(typeComment, context);
    }

    return false;
  }

  public static boolean eventuallyResolvesToFinal(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final Set<PyExpression> currentQueue = new HashSet<>();
    final Set<PyExpression> nextQueue = new HashSet<>();
    nextQueue.add(expression);
    final Set<PyExpression> visited = new HashSet<>();

    while (!nextQueue.isEmpty()) {
      currentQueue.clear();
      currentQueue.addAll(nextQueue);
      nextQueue.clear();

      for (PyExpression element : currentQueue) {
        if (resolvesToFinal(element, context, nextQueue, visited)) return true;
      }
    }

    return false;
  }

  private static boolean resolvesToFinal(@NotNull PyExpression expression,
                                         @NotNull TypeEvalContext context,
                                         @NotNull Set<PyExpression> queue,
                                         @NotNull Set<PyExpression> visited) {
    final List<Pair<PyTargetExpression, PsiElement>> pairs = tryResolvingWithAliases(expression, context);

    for (Pair<PyTargetExpression, PsiElement> pair : pairs) {
      if (pair.second instanceof PyExpression) {
        final PyExpression resolved = (PyExpression)pair.second;

        final String qualifiedName = getQualifiedName(resolved);
        if (FINAL.equals(qualifiedName) || FINAL_EXT.equals(qualifiedName)) {
          return true;
        }
        else if (pair.first != null && visited.add(resolved)) {
          queue.add(resolved);
        }
      }
    }

    return false;
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
        return toExpression(annotationText, owner);
      }
    }
    return null;
  }

  @Nullable
  private static PyExpression toExpression(@NotNull String contents, @NotNull PsiElement anchor) {
    final PsiFile file = FileContextUtil.getContextFile(anchor);
    if (file == null) return null;
    return PyUtil.createExpressionFromFragment(contents, file);
  }

  @Nullable
  public static Ref<PyType> getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull TypeEvalContext context) {
    return getStringBasedType(contents, anchor, new Context(context));
  }

  @Nullable
  private static Ref<PyType> getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull Context context) {
    final PyExpression expr = toExpression(contents, anchor);
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
  private static Ref<PyType> getVariableTypeCommentType(@NotNull String contents,
                                                        @NotNull PyTargetExpression target,
                                                        @NotNull Context context) {
    final PyExpression expr = PyPsiUtils.flattenParens(toExpression(contents, target));
    if (expr != null) {
      // Such syntax is specific to "# type:" comments, unpacking in type hints is not allowed anywhere else
      if (expr instanceof PyTupleExpression) {
        // XXX: Switches stub to AST
        final PyExpression topmostTarget = findTopmostTarget(target);
        if (topmostTarget != null) {
          final Map<PyTargetExpression, PyExpression> targetToExpr = mapTargetsToAnnotations(topmostTarget, expr);
          final PyExpression typeExpr = targetToExpr.get(target);
          if (typeExpr != null) {
            return getType(typeExpr, context);
          }
        }
      }
      else {
        return getType(expr, context);
      }
    }
    return null;
  }

  @Nullable
  private static PyExpression findTopmostTarget(@NotNull PyTargetExpression target) {
    final PyElement validTargetParent = PsiTreeUtil.getParentOfType(target, PyForPart.class, PyWithItem.class, PyAssignmentStatement.class);
    if (validTargetParent == null) {
      return null;
    }
    final PyExpression topmostTarget = as(PsiTreeUtil.findPrevParent(validTargetParent, target), PyExpression.class);
    if (validTargetParent instanceof PyForPart && topmostTarget != ((PyForPart)validTargetParent).getTarget()) {
      return null;
    }
    if (validTargetParent instanceof PyWithItem && topmostTarget != ((PyWithItem)validTargetParent).getTarget()) {
      return null;
    }
    if (validTargetParent instanceof PyAssignmentStatement &&
        ArrayUtil.indexOf(((PyAssignmentStatement)validTargetParent).getRawTargets(), topmostTarget) < 0) {
      return null;
    }
    return topmostTarget;
  }

  @NotNull
  public static Map<PyTargetExpression, PyExpression> mapTargetsToAnnotations(@NotNull PyExpression targetExpr,
                                                                              @NotNull PyExpression typeExpr) {
    final PyExpression targetsNoParen = PyPsiUtils.flattenParens(targetExpr);
    final PyExpression typesNoParen = PyPsiUtils.flattenParens(typeExpr);
    if (targetsNoParen == null || typesNoParen == null) {
      return Collections.emptyMap();
    }
    if (targetsNoParen instanceof PySequenceExpression && typesNoParen instanceof PySequenceExpression) {
      final Ref<Map<PyTargetExpression, PyExpression>> result = new Ref<>(new LinkedHashMap<>());
      mapTargetsToExpressions((PySequenceExpression)targetsNoParen, (PySequenceExpression)typesNoParen, result);
      return result.isNull() ? Collections.emptyMap() : Collections.unmodifiableMap(result.get());
    }
    else if (targetsNoParen instanceof PyTargetExpression && !(typesNoParen instanceof PySequenceExpression)) {
      return ImmutableMap.of((PyTargetExpression)targetsNoParen, typesNoParen);
    }
    return Collections.emptyMap();
  }

  private static void mapTargetsToExpressions(@NotNull PySequenceExpression targetSequence,
                                              @NotNull PySequenceExpression valueSequence,
                                              @NotNull Ref<Map<PyTargetExpression, PyExpression>> result) {
    final PyExpression[] targets = targetSequence.getElements();
    final PyExpression[] values = valueSequence.getElements();

    if (targets.length != values.length) {
      result.set(null);
      return;
    }

    for (int i = 0; i < targets.length; i++) {
      final PyExpression target = PyPsiUtils.flattenParens(targets[i]);
      final PyExpression value = PyPsiUtils.flattenParens(values[i]);

      if (target == null || value == null) {
        result.set(null);
        return;
      }

      if (target instanceof PySequenceExpression && value instanceof PySequenceExpression) {
        mapTargetsToExpressions((PySequenceExpression)target, (PySequenceExpression)value, result);
        if (result.isNull()) {
          return;
        }
      }
      else if (target instanceof PyTargetExpression && !(value instanceof PySequenceExpression)) {
        final Map<PyTargetExpression, PyExpression> map = result.get();
        assert map != null;
        map.put((PyTargetExpression)target, value);
      }
      else {
        result.set(null);
        return;
      }
    }
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
      if (operandNames.contains(UNION)) {
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
        if (calleeQNames.contains(TYPE_VAR)) {
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
  private static PyType getGenericTypeBound(PyExpression @NotNull [] typeVarArguments, @NotNull Context context) {
    final List<PyType> types = new ArrayList<>();
    for (int i = 1; i < typeVarArguments.length; i++) {
      final PyExpression argument = typeVarArguments[i];

      if (argument instanceof PyKeywordArgument) {
        if ("bound".equals(((PyKeywordArgument)argument).getKeyword())) {
          final PyExpression value = ((PyKeywordArgument)argument).getValueExpression();
          return value == null ? null : Ref.deref(getType(value, context));
        }
        else {
          break; // covariant, contravariant
        }
      }

      types.add(Ref.deref(getType(argument, context)));
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
  private static PyType getCollection(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final String typingName = getQualifiedName(element);

    final String builtinName = BUILTIN_COLLECTION_CLASSES.get(typingName);
    if (builtinName != null) return PyTypeParser.getTypeByName(element, builtinName, context);

    final String collectionName = COLLECTIONS_CLASSES.get(typingName);
    if (collectionName != null) return PyTypeParser.getTypeByName(element, collectionName, context);

    return null;
  }

  @NotNull
  private static List<PsiElement> tryResolving(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    return ContainerUtil.map(tryResolvingWithAliases(expression, context), x -> x.getSecond());
  }

  @NotNull
  private static List<Pair<PyTargetExpression, PsiElement>> tryResolvingWithAliases(@NotNull PyExpression expression,
                                                                                    @NotNull TypeEvalContext context) {
    final List<Pair<PyTargetExpression, PsiElement>> elements = new ArrayList<>();
    if (expression instanceof PyReferenceExpression) {
      final List<PsiElement> results;
      if (context.maySwitchToAST(expression)) {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
        results = PyUtil.multiResolveTopPriority(expression, resolveContext);
      }
      else {
        results = tryResolvingOnStubs((PyReferenceExpression)expression, context);
      }
      for (PsiElement element : results) {
        final PyClass cls = PyUtil.turnConstructorIntoClass(as(element, PyFunction.class));
        if (cls != null) {
          elements.add(Pair.create(null, cls));
          continue;
        }
        final String name = element != null ? getQualifiedName(element) : null;
        if (name != null && OPAQUE_NAMES.contains(name)) {
          elements.add(Pair.create(null, element));
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
            elements.add(Pair.create(targetExpr, assignedValue));
            continue;
          }
        }
        if (element != null) {
          elements.add(Pair.create(null, element));
        }
      }
    }
    return !elements.isEmpty() ? elements : Collections.singletonList(Pair.create(null, expression));
  }

  @NotNull
  private static List<PsiElement> tryResolvingOnStubs(@NotNull PyReferenceExpression expression,
                                                      @NotNull TypeEvalContext context) {

    final QualifiedName qualifiedName = expression.asQualifiedName();
    final PyFile pyFile = as(FileContextUtil.getContextFile(expression), PyFile.class);

    if (pyFile != null && qualifiedName != null) {
      return PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, pyFile, context);
    }
    return Collections.singletonList(expression);
  }

  @NotNull
  public static Collection<String> resolveToQualifiedNames(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final Set<String> names = new LinkedHashSet<String>();
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
      else if (returnType instanceof PyCollectionType && isGenerator(returnType)) {
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

  public static boolean isClassVar(@NotNull PyAnnotationOwner annotationOwner, @NotNull TypeEvalContext context) {
    final PyExpression annotationValue = getAnnotationValue(annotationOwner, context);

    if (annotationValue instanceof PySubscriptionExpression) {
      final PyExpression operand = ((PySubscriptionExpression)annotationValue).getOperand();
      return operand instanceof PyReferenceExpression && resolveToQualifiedNames(operand, context).contains(CLASS_VAR);
    }
    else if (annotationValue instanceof PyReferenceExpression) {
      return resolveToQualifiedNames(annotationValue, context).contains(CLASS_VAR);
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

  public static boolean isInAnnotationOrTypeComment(@NotNull PsiElement element) {
    final PsiElement realContext = PyPsiUtils.getRealContext(element);

    if (PsiTreeUtil.getParentOfType(realContext, PyAnnotation.class, false, ScopeOwner.class) != null) {
      return true;
    }

    final PsiComment comment = PsiTreeUtil.getParentOfType(realContext, PsiComment.class, false, ScopeOwner.class);
    if (comment != null && getTypeCommentValue(comment.getText()) != null) {
      return true;
    }

    return false;
  }

  static class Context {
    @NotNull private final TypeEvalContext myContext;
    @NotNull private final Set<PsiElement> myCache = new HashSet<>();

    Context(@NotNull TypeEvalContext context) {
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
