// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.*;
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
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.stubs.PyClassElementType;
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.psi.types.PyTypeParameterMapping.Option;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.RecursionManager.doPreventingRecursion;
import static com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL;
import static com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL_EXT;
import static com.jetbrains.python.psi.PyUtil.as;

public final class PyTypingTypeProvider extends PyTypeProviderWithCustomContext<PyTypingTypeProvider.Context> {

  public static final String TYPING = "typing";

  public static final String GENERATOR = "typing.Generator";
  public static final String ASYNC_GENERATOR = "typing.AsyncGenerator";
  public static final String COROUTINE = "typing.Coroutine";
  public static final String NAMEDTUPLE = "typing.NamedTuple";
  public static final String TYPED_DICT = "typing.TypedDict";
  public static final String TYPED_DICT_EXT = "typing_extensions.TypedDict";
  public static final String TYPE_GUARD = "typing.TypeGuard";
  public static final String TYPE_GUARD_EXT = "typing_extensions.TypeGuard";
  public static final String TYPE_IS = "typing.TypeIs";
  public static final String TYPE_IS_EXT = "typing_extensions.TypeIs";
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
  public static final String TYPE_VAR_TUPLE = "typing.TypeVarTuple";
  public static final String TYPE_VAR_TUPLE_EXT = "typing_extensions.TypeVarTuple";
  public static final String TYPING_PARAM_SPEC = "typing.ParamSpec";
  public static final String TYPING_EXTENSIONS_PARAM_SPEC = "typing_extensions.ParamSpec";
  private static final String CHAIN_MAP = "typing.ChainMap";
  public static final String UNION = "typing.Union";
  public static final String TYPING_CONCATENATE = "typing.Concatenate";
  public static final String TYPING_EXTENSIONS_CONCATENATE = "typing_extensions.Concatenate";
  public static final String OPTIONAL = "typing.Optional";
  public static final String NO_RETURN = "typing.NoReturn";
  public static final String NEVER = "typing.Never";
  public static final String NO_RETURN_EXT = "typing_extensions.NoReturn";
  public static final String NEVER_EXT = "typing_extensions.Never";
  public static final String FINAL = "typing.Final";
  public static final String FINAL_EXT = "typing_extensions.Final";
  public static final String LITERAL = "typing.Literal";
  public static final String LITERAL_EXT = "typing_extensions.Literal";
  public static final String LITERALSTRING = "typing.LiteralString";
  public static final String LITERALSTRING_EXT = "typing_extensions.LiteralString";
  public static final String ANNOTATED = "typing.Annotated";
  public static final String ANNOTATED_EXT = "typing_extensions.Annotated";
  public static final String TYPE_ALIAS = "typing.TypeAlias";
  public static final String TYPE_ALIAS_EXT = "typing_extensions.TypeAlias";
  public static final String TYPE_ALIAS_TYPE = "typing.TypeAliasType";
  private static final String SPECIAL_FORM = "typing._SpecialForm";
  private static final String SPECIAL_FORM_EXT = "typing_extensions._SpecialForm";
  public static final String REQUIRED = "typing.Required";
  public static final String REQUIRED_EXT = "typing_extensions.Required";
  public static final String NOT_REQUIRED = "typing.NotRequired";
  public static final String NOT_REQUIRED_EXT = "typing_extensions.NotRequired";
  public static final String READONLY = "typing.ReadOnly";
  public static final String READONLY_EXT = "typing_extensions.ReadOnly";

  public static final Set<String> TYPE_DICT_QUALIFIERS = Set.of(REQUIRED, REQUIRED_EXT, NOT_REQUIRED, NOT_REQUIRED_EXT, READONLY, READONLY_EXT);

  private static final String UNPACK = "typing.Unpack";
  private static final String UNPACK_EXT = "typing_extensions.Unpack";

  public static final String SELF = "typing.Self";
  public static final String SELF_EXT = "typing_extensions.Self";
  private static final String PY2_FILE_TYPE = "typing.BinaryIO";
  private static final String PY3_BINARY_FILE_TYPE = "typing.BinaryIO";
  private static final String PY3_TEXT_FILE_TYPE = "typing.TextIO";

  public static final Pattern TYPE_IGNORE_PATTERN = Pattern.compile("# *type: *ignore(\\[ *[^ ,\\]]+ *(, *[^ ,\\]]+ *)*\\])? *($|(#.*))",
                                                                    Pattern.CASE_INSENSITIVE);

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

  public static final ImmutableMap<String, String> TYPING_BUILTINS_GENERIC_ALIASES = ImmutableMap.<String, String>builder()
    .putAll(TYPING_COLLECTION_CLASSES.entrySet())
    .put("type", "Type")
    .put("tuple", "Tuple")
    .build();

  public static final ImmutableSet<String> GENERIC_CLASSES = ImmutableSet.<String>builder()
    // special forms
    .add(TUPLE, GENERIC, PROTOCOL, CALLABLE, TYPE, CLASS_VAR, FINAL, LITERAL, ANNOTATED, REQUIRED, NOT_REQUIRED)
    // type aliases
    .add(UNION, OPTIONAL, LIST, DICT, DEFAULT_DICT, ORDERED_DICT, SET, FROZEN_SET, COUNTER, DEQUE, CHAIN_MAP)
    .add(PROTOCOL_EXT, FINAL_EXT, LITERAL_EXT, ANNOTATED_EXT, REQUIRED_EXT, NOT_REQUIRED_EXT)
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
    .add(TYPE_VAR_TUPLE)
    .add(TYPE_VAR_TUPLE_EXT)
    .add(GENERIC)
    .add(TYPING_PARAM_SPEC)
    .add(TYPING_EXTENSIONS_PARAM_SPEC)
    .add(TYPING_CONCATENATE)
    .add(TYPING_EXTENSIONS_CONCATENATE)
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
    .add(ANNOTATED, ANNOTATED_EXT)
    .add(TYPE_ALIAS, TYPE_ALIAS_EXT)
    .add(REQUIRED, REQUIRED_EXT)
    .add(NOT_REQUIRED, NOT_REQUIRED_EXT)
    .add(SELF, SELF_EXT)
    .build();

  // Type hints in PSI stubs, type comments and "escaped" string annotations are represented as PyExpressionCodeFragments
  // created from the corresponding text fragments. This key points to the closest definition in the original file
  // they belong to.
  // TODO Make this the result of PyExpressionCodeFragment.getContext()
  private static final Key<PsiElement> FRAGMENT_OWNER = Key.create("PY_FRAGMENT_OWNER");
  private static final Key<Context> TYPE_HINT_EVAL_CONTEXT = Key.create("TYPE_HINT_EVAL_CONTEXT");

  @Nullable
  @Override
  public PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull Context context) {
    // Check for the exact name in advance for performance reasons
    if ("Generic".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context.myContext).contains(GENERIC)) {
        return createTypingGenericType(referenceExpression);
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Protocol".equals(referenceExpression.getName())) {
      if (ContainerUtil.exists(resolveToQualifiedNames(referenceExpression, context.myContext), n -> PROTOCOL.equals(n) || PROTOCOL_EXT.equals(n))) {
        return createTypingProtocolType(referenceExpression);
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Callable".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context.myContext).contains(CALLABLE)) {
        return createTypingCallableType(referenceExpression);
      }
    }

    return null;
  }

  @Override
  @Nullable
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull Context context) {
    @Nullable PyExpression typeHint = getAnnotationValue(param, context.myContext);
    if (typeHint == null) {
      String paramTypeCommentHint = param.getTypeCommentAnnotation();
      if (paramTypeCommentHint != null) {
        typeHint = toExpression(paramTypeCommentHint, param);
      }
    }
    if (typeHint == null) {
      PyFunctionTypeAnnotation annotation = getFunctionTypeAnnotation(func);
      if (annotation != null) {
        PyExpression funcTypeCommentParamHint = findParamTypeHintInFunctionTypeComment(annotation, param, func);
        if (funcTypeCommentParamHint == null) {
          return Ref.create();
        }
        typeHint = funcTypeCommentParamHint;
      }
    }
    if (typeHint == null) {
      return null;
    }
    if (param.isKeywordContainer()) {
      Ref<PyType> type = getTypeFromUnpackOperator(typeHint, context.myContext);
      if (type != null) {
        return type.get() instanceof PyTypedDictType ? type : null;
      }
    }
    if (typeHint instanceof PyReferenceExpression ref && ref.isQualified() && (
      param.isPositionalContainer() && "args".equals(ref.getReferencedName()) ||
      param.isKeywordContainer() && "kwargs".equals(ref.getReferencedName())
    )) {
      typeHint = Objects.requireNonNull(ref.getQualifier());
    }
    PyType type = Ref.deref(getType(typeHint, context));
    if (param.isPositionalContainer() && !(type instanceof PyParamSpecType)) {
      return Ref.create(PyTypeUtil.toPositionalContainerType(param, type));
    }
    if (param.isKeywordContainer() && !(type instanceof PyParamSpecType)) {
      return Ref.create(PyTypeUtil.toKeywordContainerType(param, type));
    }
    if (PyNames.NONE.equals(param.getDefaultValueText())) {
      return Ref.create(PyUnionType.union(type, PyNoneType.INSTANCE));
    }
    return Ref.create(type);
  }

  private static @Nullable PyExpression findParamTypeHintInFunctionTypeComment(@NotNull PyFunctionTypeAnnotation annotation,
                                                                               @NotNull PyNamedParameter param,
                                                                               @NotNull PyFunction func) {
    List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
    if (paramTypes.size() == 1 && paramTypes.get(0) instanceof PyNoneLiteralExpression noneExpr && noneExpr.isEllipsis()) {
      return null;
    }
    int startOffset = omitFirstParamInTypeComment(func, annotation) ? 1 : 0;
    List<PyParameter> funcParams = Arrays.asList(func.getParameterList().getParameters());
    int i = funcParams.indexOf(param) - startOffset;
    if (i >= 0 && i < paramTypes.size()) {
      PyExpression paramTypeHint = paramTypes.get(i);
      if (paramTypeHint instanceof PyStarExpression starExpression) {
        return starExpression.getExpression();
      }
      else if (paramTypeHint instanceof PyDoubleStarExpression doubleStarExpression) {
        return doubleStarExpression.getExpression();
      }
      else {
        return paramTypeHint;
      }
    }
    return null;
  }

  public static boolean isGenerator(@NotNull PyType type) {
    return type instanceof PyCollectionType genericType && GENERATOR.equals(genericType.getClassQName());
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
    return func.getContainingClass() != null && func.getModifier() != PyAstFunction.Modifier.STATICMETHOD &&
           annotation.getParameterTypeList().getParameterTypes().size() < func.getParameterList().getParameters().length;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull Context context) {
    if (callable instanceof PyFunction function) {

      if (getTypeGuardKind(function, context.myContext) != TypeGuardKind.None) {
        return Ref.create(PyBuiltinCache.getInstance(callable).getBoolType());
      }      
      final PyExpression returnTypeAnnotation = getReturnTypeAnnotation(function, context.myContext);
      if (returnTypeAnnotation != null) {
        final Ref<PyType> typeRef = getType(returnTypeAnnotation, context);
        if (typeRef != null) {
          return Ref.create(toAsyncIfNeeded(function, typeRef.get()));
        }
        // Don't rely on other type providers if a type hint is present, but cannot be resolved.
        return Ref.create();
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable PyExpression getReturnTypeAnnotation(@NotNull PyFunction function, TypeEvalContext context) {
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
  public Ref<PyType> getCallType(@NotNull PyFunction function, @NotNull PyCallSiteExpression callSite, @NotNull Context context) {
    final String functionQName = function.getQualifiedName();

    if ("typing.cast".equals(functionQName)) {
      return Optional
        .ofNullable(as(callSite, PyCallExpression.class))
        .map(PyCallExpression::getArguments)
        .filter(args -> args.length > 0)
        .map(args -> getType(args[0], context))
        .orElse(null);
    }

    if (callSite instanceof PyCallExpression callExpression) {
      var typeGuardKind = getTypeGuardKind(function, context.myContext);
      if (typeGuardKind != TypeGuardKind.None) {
        var arguments = callSite.getArguments(function);
        if (!arguments.isEmpty() && arguments.get(0) instanceof PyReferenceExpression refExpr) {
          var qname = PyPsiUtilsCore.asQualifiedName(refExpr);
          if (qname != null) {
            var narrowedType = getTypeFromTypeGuardLikeType(function, context.myContext);
            if (narrowedType != null) {
              return Ref.create(PyNarrowedType.Companion.create(callSite,
                                                                qname.toString(),
                                                                narrowedType,
                                                                callExpression,
                                                                false,
                                                                TypeGuardKind.TypeIs.equals(typeGuardKind)));
            }
          }
        }
        return Ref.create(PyBuiltinCache.getInstance(function).getBoolType());
      }
    }

    if (callSite instanceof PyCallExpression) {
      final LanguageLevel level = "open".equals(functionQName)
                                  ? LanguageLevel.forElement(callSite)
                                  : "pathlib.Path.open".equals(functionQName) || "_io.open".equals(functionQName)
                                    ? LanguageLevel.PYTHON34
                                    : null;

      if (level != null) {
        return getOpenFunctionCallType(function, (PyCallExpression)callSite, level, context.myContext);
      }
    }

    final PyClass initializedClass = PyUtil.turnConstructorIntoClass(function);
    if (initializedClass != null && (TYPE_VAR.equals(initializedClass.getQualifiedName()) ||
                                     TYPE_VAR_TUPLE.equals(initializedClass.getQualifiedName()) ||
                                     TYPE_VAR_TUPLE_EXT.equals(initializedClass.getQualifiedName()))) {
      // `typing.TypeVar` call should be assigned to a target and hence should be processed by [getReferenceType]
      // but the corresponding type is also returned here to suppress type checker on `T = TypeVar("T")` assignment.
      return Ref.create(getTypeParameterTypeFromDeclaration(callSite, context));
    }

    if (initializedClass != null && callSite instanceof PyCallExpression && PyNames.DICT.equals(initializedClass.getQualifiedName())) {
      final PyType inferredTypedDict =
        PyTypedDictTypeProvider.Companion.inferTypedDictFromCallExpression((PyCallExpression)callSite, context.myContext);
      if (inferredTypedDict != null) {
        return Ref.create(inferredTypedDict);
      }
    }

    if (functionReturningCallSiteAsAType(function)) {
      return getAsClassObjectType(callSite, context);
    }

    return null;
  }

  private static boolean functionReturningCallSiteAsAType(@NotNull PyFunction function) {
    final String name = function.getName();

    if (PyNames.CLASS_GETITEM.equals(name)) return true;
    if (PyNames.GETITEM.equals(name)) {
      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        final String qualifiedName = cls.getQualifiedName();
        return SPECIAL_FORM.equals(qualifiedName) || SPECIAL_FORM_EXT.equals(qualifiedName);
      }
    }

    return false;
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
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull Context context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression target) {
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

      final PyType collection = getCollection(target, context.myContext);
      if (collection instanceof PyInstantiableType) {
        return Ref.create(((PyInstantiableType<?>)collection).toClass());
      }

      final PyType typedDictType = getTypedDictTypeForTarget(target, context.myContext);
      if (typedDictType != null) {
        return Ref.create(typedDictType);
      }

      final Ref<PyType> annotatedType = getTypeFromTargetExpressionAnnotation(target, context);
      if (annotatedType != null) {
        return annotatedType;
      }

      final PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(target);
      if (assignedValue != null) {
        final PyType type = getTypeParameterTypeFromDeclaration(assignedValue, context);
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
          final PyResolveContext resolveContext = PyResolveContext.defaultContext(context.myContext);

          boolean isInstanceAttribute;
          if (context.myContext.maySwitchToAST(target)) {
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
        if (context.myContext.maySwitchToAST(target)) {
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
  private static Ref<PyType> getTypeFromTargetExpressionAnnotation(@NotNull PyTargetExpression target, @NotNull Context context) {
    final PyExpression annotation = getAnnotationValue(target, context.myContext);
    if (annotation != null) {
      return getType(annotation, context);
    }
    final String comment = target.getTypeCommentAnnotation();
    if (comment != null) {
      return getVariableTypeCommentType(comment, target, context);
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
    return PyUtilCore.getTypeCommentValue(text);
  }

  /**
   * Returns the corresponding text range for a type hint as returned by {@link #getTypeCommentValue(String)}.
   *
   * @see #getTypeCommentValue(String)
   */
  @Nullable
  public static TextRange getTypeCommentValueRange(@NotNull String text) {
    return PyUtilCore.getTypeCommentValueRange(text);
  }

  @Nullable
  @Override
  public PyType getGenericType(@NotNull PyClass cls, @NotNull Context context) {
    List<PyTypeParameterType> typeParameters = collectTypeParameters(cls, context);
    return typeParameters.isEmpty() ? null : new PyCollectionTypeImpl(cls, false, typeParameters);
  }

  @NotNull
  @Override
  public Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull Context context) {
    return PyUtil.getParameterizedCachedValue(cls, context, c -> calculateGenericSubstitutions(cls, c));
  }

  @NotNull
  private Map<PyType, PyType> calculateGenericSubstitutions(@NotNull PyClass cls, @NotNull Context context) {
    if (!isGeneric(cls, context.myContext)) {
      return Collections.emptyMap();
    }
    Map<PyType, PyType> results = new HashMap<>();
    for (PyClassType superClassType : evaluateSuperClassesAsTypeHints(cls, context.myContext)) {
      Map<PyType, PyType> superSubstitutions =
        doPreventingRecursion(superClassType.getPyClass(), false, () -> getGenericSubstitutions(superClassType.getPyClass(), context));
      if (superSubstitutions != null) {
        results.putAll(superSubstitutions);
      }
      // TODO Share this logic with PyTypeChecker.collectTypeSubstitutions
      List<PyTypeParameterType> superTypeParameters = collectTypeParameters(superClassType.getPyClass(), context);
      List<PyType> superTypeArguments = superClassType instanceof PyCollectionType parameterized ?
                                        parameterized.getElementTypes() : Collections.emptyList();
      PyTypeParameterMapping mapping =
        PyTypeParameterMapping.mapByShape(superTypeParameters, superTypeArguments, Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
      if (mapping != null) {
        for (Couple<PyType> pair : mapping.getMappedTypes()) {
          PyType expectedType = pair.getFirst();
          PyType actualType = pair.getSecond();
          if (!expectedType.equals(actualType)) {
            results.put(expectedType, actualType);
          }
        }
      }
    }
    return results;
  }

  private static @NotNull List<PyClassType> evaluateSuperClassesAsTypeHints(@NotNull PyClass pyClass, @NotNull TypeEvalContext context) {
    List<PyClassType> results = new ArrayList<>();
    for (PyExpression superClassExpression : PyClassElementType.getSuperClassExpressions(pyClass)) {
      PsiFile containingFile = superClassExpression.getContainingFile();
      if (containingFile instanceof PyExpressionCodeFragment) {
        containingFile.putUserData(FRAGMENT_OWNER, pyClass);
      }
      PyType type = Ref.deref(getType(superClassExpression, context));
      if (type instanceof PyClassType classType) {
        results.add(classType);
      }
    }
    return results;
  }

  @NotNull
  private static List<PyTypeParameterType> collectTypeParameters(@NotNull PyClass cls, @NotNull Context context) {
    if (!isGeneric(cls, context.getTypeContext())) {
      return Collections.emptyList();
    }
    if (cls.getTypeParameterList() != null) {
      List<PyTypeParameter> typeParameters = cls.getTypeParameterList().getTypeParameters();
      return StreamEx.of(typeParameters)
        .map(typeParameter -> getTypeParameterTypeFromTypeParameter(typeParameter, context))
        .nonNull()
        .toList();
    }
    // See https://mypy.readthedocs.io/en/stable/generics.html#defining-sub-classes-of-generic-classes
    List<PySubscriptionExpression> parameterizedSuperClassExpressions =
      ContainerUtil.filterIsInstance(PyClassElementType.getSuperClassExpressions(cls), PySubscriptionExpression.class);
    PySubscriptionExpression genericAsSuperClass = ContainerUtil.find(parameterizedSuperClassExpressions, s -> {
      return resolveToQualifiedNames(s.getOperand(), context.myContext).contains(GENERIC);
    });
    return StreamEx.of(genericAsSuperClass != null ? Collections.singletonList(genericAsSuperClass) : parameterizedSuperClassExpressions)
      .peek(expr -> {
        PsiFile containingFile = expr.getContainingFile();
        if (containingFile instanceof PyExpressionCodeFragment) {
          containingFile.putUserData(FRAGMENT_OWNER, cls);
        }
      })
      .map(PySubscriptionExpression::getIndexExpression)
      .flatMap(e -> {
        final PyTupleExpression tupleExpr = as(e, PyTupleExpression.class);
        return tupleExpr != null ? StreamEx.of(tupleExpr.getElements()) : StreamEx.of(e);
      })
      .nonNull()
      .map(e -> getType(e, context))
      .map(Ref::deref)
      .flatMap(type -> {
        PyTypeChecker.Generics typeParams = PyTypeChecker.collectGenerics(type, context.myContext);
        return StreamEx.<PyType>of(typeParams.getTypeVars()).append(typeParams.getTypeVarTuples()).append(StreamEx.of(typeParams.getParamSpecs()));
      })
      .select(PyTypeParameterType.class)
      .distinct()
      .toList();
  }

  public static boolean isGeneric(@NotNull PyWithAncestors descendant, @NotNull TypeEvalContext context) {
    if (descendant instanceof PyClass pyClass && pyClass.getTypeParameterList() != null ||
        descendant instanceof PyClassType pyClassType && pyClassType.getPyClass().getTypeParameterList() != null) {
      return true;
    }
    for (PyClassLikeType ancestor : descendant.getAncestorTypes(context)) {
      if (ancestor != null) {
        if (GENERIC_CLASSES.contains(ancestor.getClassQName())) {
          return true;
        }
        else if (ancestor instanceof PyClassType classType &&
                 classType.getPyClass().getTypeParameterList() != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static Ref<PyType> getType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    return staticWithCustomContext(context, customContext -> getType(expression, customContext));
  }

  @Nullable
  private static Ref<PyType> getType(@NotNull PyExpression expression, @NotNull Context context) {
    final List<PyType> members = new ArrayList<>();
    boolean foundAny = false;
    for (Pair<PyQualifiedNameOwner, PsiElement> pair : tryResolvingWithAliases(expression, context.getTypeContext())) {
      final Ref<PyType> typeRef = getTypeForResolvedElement(expression, pair.getFirst(), pair.getSecond(), context);
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
  private static Ref<PyType> getTypeFromBitwiseOrOperator(@NotNull PyBinaryExpression expression, @NotNull Context context) {
    if (expression.getOperator() != PyTokenTypes.OR) return null;

    PyExpression left = expression.getLeftExpression();
    PyExpression right = expression.getRightExpression();
    if (left == null || right == null) return null;

    Ref<PyType> leftTypeRef = getType(left, context);
    Ref<PyType> rightTypeRef = getType(right, context);
    if (leftTypeRef == null || rightTypeRef == null) return null;

    PyType leftType = leftTypeRef.get();
    if (leftType != null && typeHasOverloadedBitwiseOr(leftType, left, context)) return null;

    PyType union = PyUnionType.union(leftType, rightTypeRef.get());
    return union != null ? Ref.create(union) : null;
  }

  private static boolean typeHasOverloadedBitwiseOr(@NotNull PyType type, @NotNull PyExpression expression,
                                                    @NotNull Context context) {
    if (type instanceof PyUnionType) return false;

    PyType typeToClass = type instanceof PyClassLikeType ? ((PyClassLikeType)type).toClass() : type;
    var resolved = typeToClass.resolveMember("__or__", expression, AccessDirection.READ,
                                             PyResolveContext.defaultContext(context.getTypeContext()));
    if (resolved == null || resolved.isEmpty()) return false;

    return StreamEx.of(resolved)
      .map(it -> it.getElement())
      .nonNull()
      .noneMatch(it -> PyBuiltinCache.getInstance(it).isBuiltin(it));
  }

  public static boolean isBitwiseOrUnionAvailable(@NotNull TypeEvalContext context) {
    final PsiFile originFile = context.getOrigin();
    return originFile == null || isBitwiseOrUnionAvailable(originFile);
  }

  public static boolean isBitwiseOrUnionAvailable(@NotNull PsiElement element) {
    if (LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON310)) return true;

    PsiFile file = element.getContainingFile();
    if (file instanceof PyFile && ((PyFile)file).hasImportFromFuture(FutureFeature.ANNOTATIONS)) {
      return file == element || PsiTreeUtil.getParentOfType(element, PyAnnotation.class, false, PyStatement.class) != null;
    }

    return false;
  }

  @Nullable
  private static Ref<PyType> getTypeForResolvedElement(@NotNull PyExpression typeHint,
                                                       @Nullable PyQualifiedNameOwner alias,
                                                       @NotNull PsiElement resolved,
                                                       @NotNull Context context) {
    if (alias != null) {
      if (context.getTypeAliasStack().contains(alias)) {
        // Recursive types are not yet supported
        return null;
      }
      context.getTypeAliasStack().add(alias);
    }
    try {
      final Ref<PyType> typeFromParenthesizedExpression = getTypeFromParenthesizedExpression(resolved, context);
      if (typeFromParenthesizedExpression != null) {
        return typeFromParenthesizedExpression;
      }
      final PyType unionType = getUnionType(resolved, context);
      if (unionType != null) {
        return Ref.create(unionType);
      }
      final PyType concatenateType = getConcatenateType(resolved, context);
      if (concatenateType != null) {
        return Ref.create(concatenateType);
      }
      final Ref<PyType> optionalType = getOptionalType(resolved, context);
      if (optionalType != null) {
        return optionalType;
      }
      final PyType callableType = getCallableType(resolved, context);
      if (callableType != null) {
        return Ref.create(callableType);
      }
      final Ref<PyType> classVarType = unwrapTypeModifier(resolved, context, CLASS_VAR);
      if (classVarType != null) {
        return classVarType;
      }
      final Ref<PyType> classObjType = getClassObjectType(resolved, context);
      if (classObjType != null) {
        return classObjType;
      }
      final Ref<PyType> finalType = unwrapTypeModifier(resolved, context, FINAL, FINAL_EXT);
      if (finalType != null) {
        return finalType;
      }
      final Ref<PyType> annotatedType = getAnnotatedType(resolved, context);
      if (annotatedType != null) {
        return annotatedType;
      }
      final Ref<PyType> requiredOrNotRequiredType = getRequiredOrNotRequiredType(resolved, context);
      if (requiredOrNotRequiredType != null) {
        return requiredOrNotRequiredType;
      }
      final Ref<PyType> literalStringType = getLiteralStringType(resolved, context);
      if (literalStringType != null) {
        return literalStringType;
      }
      final Ref<PyType> literalType = getLiteralType(resolved, context);
      if (literalType != null) {
        return literalType;
      }
      final Ref<PyType> typeAliasType = getExplicitTypeAliasType(resolved);
      if (typeAliasType != null) {
        return typeAliasType;
      }
      final PyType parameterizedType = getParameterizedType(resolved, context);
      if (parameterizedType != null) {
        return Ref.create(parameterizedType);
      }
      final PyType collection = getCollection(resolved, context.getTypeContext());
      if (collection != null) {
        return Ref.create(collection);
      }
      final PyType typeParameter = getTypeParameterTypeFromDeclaration(resolved, context);
      if (typeParameter != null) {
        return Ref.create(anchorTypeParameter(typeHint, typeParameter, context));
      }
      final PyType unpackedType = getUnpackedType(resolved, context.getTypeContext());
      if (unpackedType != null) {
        return Ref.create(unpackedType);
      }
      final PyType typeParameterType = getTypeParameterTypeFromTypeParameter(resolved, context);
      if (typeParameterType != null) {
        return Ref.create(typeParameterType);
      }
      final PyType paramSpecType = getParamSpecType(resolved, context);
      if (paramSpecType != null) {
        return Ref.create(anchorTypeParameter(typeHint, paramSpecType, context));
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
      final Ref<PyType> unionTypeFromBinaryOr = getTypeFromBinaryExpression(resolved, context);
      if (unionTypeFromBinaryOr != null) {
        return unionTypeFromBinaryOr;
      }
      final Ref<PyType> selfType = getSelfType(resolved, typeHint, context);
      if (selfType != null) {
        return selfType;
      }
      return null;
    }
    finally {
      if (alias != null) {
        context.getTypeAliasStack().remove(alias);
      }
    }
  }

  private static Ref<PyType> getSelfType(@NotNull PsiElement resolved, @NotNull PyExpression typeHint, @NotNull Context context) {
    if (resolved instanceof PyQualifiedNameOwner &&
        (SELF.equals(((PyQualifiedNameOwner)resolved).getQualifiedName()) ||
         SELF_EXT.equals(((PyQualifiedNameOwner)resolved).getQualifiedName()))) {
      PsiElement typeHintContext = getStubRetainedTypeHintContext(typeHint);

      PyClass containingClass = typeHintContext instanceof PyClass ? (PyClass)typeHintContext
                                                                   : PsiTreeUtil.getStubOrPsiParentOfType(typeHintContext, PyClass.class);
      if (containingClass == null) return null;

      PyClassType scopeClassType = as(containingClass.getType(context.getTypeContext()), PyClassType.class);
      if (scopeClassType == null) return null;

      return Ref.create(new PySelfType(scopeClassType));
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getTypeFromBinaryExpression(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyBinaryExpression) {
      return getTypeFromBitwiseOrOperator((PyBinaryExpression)resolved, context);
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getTypeFromParenthesizedExpression(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyParenthesizedExpression) {
      final PyExpression containedExpression = PyPsiUtils.flattenParens((PyExpression)resolved);
      return containedExpression != null ? getType(containedExpression, context) : null;
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getExplicitTypeAliasType(@NotNull PsiElement resolved) {
    if (resolved instanceof PyQualifiedNameOwner) {
      String qualifiedName = ((PyQualifiedNameOwner)resolved).getQualifiedName();
      if (TYPE_ALIAS.equals(qualifiedName) || TYPE_ALIAS_EXT.equals(qualifiedName)) {
        return Ref.create();
      }
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getAliasedType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyReferenceExpression && ((PyReferenceExpression)resolved).asQualifiedName() != null) {
      return getType((PyExpression)resolved, context);
    }
    return null;
  }

  @Nullable
  private static PyType anchorTypeParameter(@NotNull PyExpression typeHint, @Nullable PyType type, @NotNull Context context) {
    PyQualifiedNameOwner typeParamDefinitionFromStack = context.getTypeAliasStack().isEmpty() ? null : context.getTypeAliasStack().peek();
    assert typeParamDefinitionFromStack == null || typeParamDefinitionFromStack instanceof PyTargetExpression;
    PyTargetExpression targetExpr = (PyTargetExpression)typeParamDefinitionFromStack;
    if (type instanceof PyTypeVarTypeImpl typeVar) {
      return typeVar.withScopeOwner(getTypeParameterScope(typeVar.getName(), typeHint, context)).withTargetExpression(targetExpr);
    }
    if (type instanceof PyParamSpecType paramSpec) {
      return paramSpec.withScopeOwner(getTypeParameterScope(paramSpec.getName(), typeHint, context)).withTargetExpression(targetExpr);
    }
    if (type instanceof PyTypeVarTupleTypeImpl typeVarTuple) {
      return typeVarTuple.withScopeOwner(getTypeParameterScope(typeVarTuple.getName(), typeHint, context)).withTargetExpression(targetExpr);
    }
    return type;
  }

  @Nullable
  private static Ref<PyType> getClassObjectType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression subsExpr) {
      final PyExpression operand = subsExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(TYPE) || operandNames.contains(PyNames.TYPE)) {
        final PyExpression indexExpr = subsExpr.getIndexExpression();
        if (indexExpr != null) {
          if (resolveToQualifiedNames(indexExpr, context.getTypeContext()).contains(ANY)) {
            return Ref.create(PyBuiltinCache.getInstance(resolved).getTypeType());
          }
          return getAsClassObjectType(indexExpr, context);
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

  @NotNull
  private static Ref<PyType> getAsClassObjectType(@NotNull PyExpression expression, @NotNull Context context) {
    final PyType type = Ref.deref(getType(expression, context));
    final PyClassType classType = as(type, PyClassType.class);
    if (classType != null && !classType.isDefinition()) {
      return Ref.create(classType.toClass());
    }
    final PyTypeVarType typeVar = as(type, PyTypeVarType.class);
    if (typeVar != null && !typeVar.isDefinition()) {
      return Ref.create(typeVar.toClass());
    }
    // Represent Type[Union[str, int]] internally as Union[Type[str], Type[int]]
    final PyUnionType unionType = as(type, PyUnionType.class);
    if (unionType != null &&
        unionType.getMembers().stream().allMatch(t -> t instanceof PyClassType && !((PyClassType)t).isDefinition())) {
      return Ref.create(PyUnionType.union(ContainerUtil.map(unionType.getMembers(), t -> ((PyClassType)t).toClass())));
    }
    return Ref.create();
  }

  @Nullable
  private static Ref<PyType> getAnyType(@NotNull PsiElement element) {
    return ANY.equals(getQualifiedName(element)) ? Ref.create() : null;
  }

  @Nullable
  private static Ref<PyType> getClassType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);
      if (type instanceof PyClassLikeType classType) {
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
    if (element instanceof PySubscriptionExpression subscriptionExpr) {
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
  private static Ref<PyType> getLiteralStringType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyTargetExpression referenceExpression) {
      Collection<String> operandNames = resolveToQualifiedNames(referenceExpression, context.getTypeContext());
      if (ContainerUtil.exists(operandNames, name -> name.equals(LITERALSTRING) || name.equals(LITERALSTRING_EXT))) {
        return Ref.create(PyLiteralStringType.Companion.create(resolved));
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getLiteralType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {

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
  private static Ref<PyType> getAnnotatedType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();

      Collection<String> resolvedNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (resolvedNames.stream().anyMatch(name -> ANNOTATED.equals(name) || ANNOTATED_EXT.equals(name))) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        final PyExpression type = indexExpr instanceof PyTupleExpression ? ((PyTupleExpression)indexExpr).getElements()[0] : indexExpr;
        if (type != null) {
          return getType(type, context);
        }
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getRequiredOrNotRequiredType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();

      Collection<String> resolvedNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (resolvedNames.stream().anyMatch(name -> REQUIRED.equals(name) || REQUIRED_EXT.equals(name) ||
                                                  NOT_REQUIRED.equals(name) || NOT_REQUIRED_EXT.equals(name))) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        final PyExpression type = indexExpr instanceof PyTupleExpression ? ((PyTupleExpression)indexExpr).getElements()[0] : indexExpr;
        if (type != null) {
          return getType(type, context);
        }
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> unwrapTypeModifier(@NotNull PsiElement resolved, @NotNull Context context, String... type) {
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {
      if (resolvesToQualifiedNames(subscriptionExpr.getOperand(), context.getTypeContext(), type)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr != null) {
          return getType(indexExpr, context);
        }
      }
    }

    return null;
  }

  private static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean typeHintedWithName(@NotNull T owner,
                                                                                               @NotNull TypeEvalContext context,
                                                                                               String... names) {
    return ContainerUtil.exists(names, resolveTypeHintsToQualifiedNames(owner, context)::contains);
  }

  private static <T extends PyTypeCommentOwner & PyAnnotationOwner> Collection<String> resolveTypeHintsToQualifiedNames(
    @NotNull T owner,
    @NotNull TypeEvalContext context
  ) {
    var annotation = getAnnotationValue(owner, context);
    if (annotation instanceof PyStringLiteralExpression stringLiteralExpression) {
      final var annotationText = stringLiteralExpression.getStringValue();
      annotation = toExpression(annotationText, owner);
      if (annotation == null) return Collections.emptyList();
    }

    if (annotation instanceof PySubscriptionExpression pySubscriptionExpression) {
      return resolveToQualifiedNames(pySubscriptionExpression.getOperand(), context);
    }
    else if (annotation instanceof PyReferenceExpression) {
      return resolveToQualifiedNames(annotation, context);
    }

    final String typeCommentValue = owner.getTypeCommentAnnotation();
    final PyExpression typeComment = typeCommentValue == null ? null : toExpression(typeCommentValue, owner);
    if (typeComment instanceof PySubscriptionExpression pySubscriptionExpression) {
      return resolveToQualifiedNames(pySubscriptionExpression.getOperand(), context);
    }
    else if (typeComment instanceof PyReferenceExpression) {
      return resolveToQualifiedNames(typeComment, context);
    }

    return Collections.emptyList();
  }

  public static boolean isFinal(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    return ContainerUtil.exists(PyKnownDecoratorUtil.getKnownDecorators(decoratable, context),
                                d -> d == TYPING_FINAL || d == TYPING_FINAL_EXT);
  }

  public static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean isFinal(@NotNull T owner, @NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(owner, context, p ->
      typeHintedWithName(owner, context, FINAL, FINAL_EXT));
  }

  public static <T extends PyAnnotationOwner & PyTypeCommentOwner> boolean isClassVar(@NotNull T owner, @NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(owner, context, p ->
      typeHintedWithName(owner, context, CLASS_VAR));
  }

  public static boolean isNoReturn(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(function, context, p ->
      typeHintedWithName(function, context, NO_RETURN, NO_RETURN_EXT, NEVER, NEVER_EXT));
  }

  public static TypeGuardKind getTypeGuardKind(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(function, context, p -> {
                                                var typeHints = resolveTypeHintsToQualifiedNames(function, context);
                                                if (typeHints.contains(TYPE_GUARD) || typeHints.contains(TYPE_GUARD_EXT)) return TypeGuardKind.TypeGuard;
                                                if (typeHints.contains(TYPE_IS) || typeHints.contains(TYPE_IS_EXT)) return TypeGuardKind.TypeIs;
                                                return TypeGuardKind.None;
                                              });
  }


  @Nullable
  public static PyType getTypeFromTypeGuardLikeType(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    var returnType = getReturnTypeAnnotation(function, context);
    if (returnType instanceof PyStringLiteralExpression stringLiteralExpression) {
      returnType = PyUtil.createExpressionFromFragment(stringLiteralExpression.getStringValue(),
                                                       function.getContainingFile());
    }
    if (returnType instanceof PySubscriptionExpression subscriptionExpression) {
      var indexExpression = subscriptionExpression.getIndexExpression();
      if (indexExpression != null) {
        return Ref.deref(getType(indexExpression, context));
      }
    }
    return null;
  }

  private static boolean resolvesToQualifiedNames(@NotNull PyExpression expression, @NotNull TypeEvalContext context, String... names) {
    final var qualifiedNames = resolveToQualifiedNames(expression, context);
    return ContainerUtil.exists(names, qualifiedNames::contains);
  }

  @ApiStatus.Internal
  public static @Nullable PyExpression getAnnotationValue(@NotNull PyAnnotationOwner owner, @NotNull TypeEvalContext context) {
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
    PyExpression fragment = PyUtil.createExpressionFromFragment(contents, file);
    if (fragment != null) {
      fragment.getContainingFile().putUserData(FRAGMENT_OWNER, anchor);
    }
    return fragment;
  }

  @Nullable
  public static Ref<PyType> getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull TypeEvalContext context) {
    return staticWithCustomContext(context, c -> getStringBasedType(contents, anchor, c));
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
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(CALLABLE)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr instanceof PyTupleExpression tupleExpr) {
          final PyExpression[] elements = tupleExpr.getElements();
          if (elements.length == 2) {
            final PyExpression parametersExpr = elements[0];
            final PyExpression returnTypeExpr = elements[1];
            if (parametersExpr instanceof PyListLiteralExpression listExpr) {
              final List<PyCallableParameter> parameters = new ArrayList<>();
              for (PyExpression argExpr : listExpr.getElements()) {
                parameters.add(PyCallableParameterImpl.nonPsi(Ref.deref(getType(argExpr, context))));
              }
              final PyType returnType = Ref.deref(getType(returnTypeExpr, context));
              return new PyCallableTypeImpl(parameters, returnType);
            }
            if (isEllipsis(parametersExpr)) {
              return new PyCallableTypeImpl(null, Ref.deref(getType(returnTypeExpr, context)));
            }
            if (parametersExpr instanceof PyReferenceExpression) {
              if (Ref.deref(getType(parametersExpr, context.myContext)) instanceof PyParamSpecType paramSpec) {
                final var parameter = PyCallableParameterImpl.nonPsi(parametersExpr.getName(), paramSpec);
                return new PyCallableTypeImpl(Collections.singletonList(parameter), Ref.deref(getType(returnTypeExpr, context)));
              }
            }
            if (parametersExpr instanceof PySubscriptionExpression && isConcatenate(parametersExpr, context.myContext)) {
              final var concatenateParameters = getConcatenateParametersTypes((PySubscriptionExpression)parametersExpr, context.myContext);
              if (concatenateParameters != null) {
                final var concatenate = new PyConcatenateType(concatenateParameters.first, concatenateParameters.second);
                final var parameter = PyCallableParameterImpl.nonPsi(parametersExpr.getName(), concatenate);
                return new PyCallableTypeImpl(Collections.singletonList(parameter), Ref.deref(getType(returnTypeExpr, context)));
              }
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

  public static boolean isParamSpec(@NotNull PyExpression parametersExpr, @NotNull TypeEvalContext context) {
    final var resolveContext = PyResolveContext.defaultContext(context);
    return PyUtil.multiResolveTopPriority(parametersExpr, resolveContext).stream().anyMatch(it -> {
      if (!(it instanceof PyTypedElement)) return false;
      final var type = context.getType((PyTypedElement)it);
      return isParamSpec(type);
    });
  }

  private static boolean isParamSpec(@Nullable PyType type) {
    if (type instanceof PyClassLikeType) {
      String classQName = ((PyClassLikeType)type).getClassQName();
      return TYPING_PARAM_SPEC.equals(classQName) || TYPING_EXTENSIONS_PARAM_SPEC.equals(classQName);
    }
    else if (type instanceof PyUnionType) {
      return ((PyUnionType)type).getMembers().stream().anyMatch(it -> isParamSpec(it));
    }
    return false;
  }

  public static boolean isConcatenate(@NotNull PyExpression parametersExpr, @NotNull TypeEvalContext context) {
    if (!(parametersExpr instanceof PySubscriptionExpression)) return false;
    final var type = Ref.deref(getType(parametersExpr, context));
    return type instanceof PyConcatenateType;
  }

  @Nullable
  private static PyType getUnionType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(UNION)) {
        return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
      }
    }
    return null;
  }

  @Nullable
  private static PyType getConcatenateType(@NotNull PsiElement element, @NotNull Context context) {
    if (!(element instanceof PySubscriptionExpression subscriptionExpr)) return null;

    final var operand = subscriptionExpr.getOperand();
    final var operandNames = resolveToQualifiedNames(operand, context.myContext);
    if (!operandNames.contains(TYPING_CONCATENATE) && !operandNames.contains(TYPING_EXTENSIONS_CONCATENATE)) return null;

    final var parameters = getConcatenateParametersTypes(subscriptionExpr, context.myContext);
    if (parameters == null) return null;

    return new PyConcatenateType(parameters.first, parameters.second);
  }

  @Nullable
  private static Pair<List<PyType>, PyParamSpecType> getConcatenateParametersTypes(@NotNull PySubscriptionExpression subscriptionExpression,
                                                                                   @NotNull TypeEvalContext context) {
    final var tuple = subscriptionExpression.getIndexExpression();
    if (!(tuple instanceof PyTupleExpression)) return null;
    final var result = ContainerUtil.mapNotNull(((PyTupleExpression)tuple).getElements(),
                                                it -> Ref.deref(getType(it, context)));
    if (result.size() < 2) return null;
    PyType lastParameter = result.get(result.size() - 1);
    if (!(lastParameter instanceof PyParamSpecType)) return null;
    return new Pair<>(result.subList(0, result.size() - 1), (PyParamSpecType)lastParameter);
  }

  @Nullable
  private static PyTypeParameterType getTypeParameterTypeFromDeclaration(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyCallExpression assignedCall) {
      final PyExpression callee = assignedCall.getCallee();
      if (callee != null) {
        final Collection<String> calleeQNames = resolveToQualifiedNames(callee, context.getTypeContext());
        if (calleeQNames.contains(TYPE_VAR) || calleeQNames.contains(TYPE_VAR_TUPLE) || calleeQNames.contains(TYPE_VAR_TUPLE_EXT)) {
          final PyExpression[] arguments = assignedCall.getArguments();
          if (arguments.length > 0) {
            final PyExpression firstArgument = arguments[0];
            if (firstArgument instanceof PyStringLiteralExpression) {
              final String name = ((PyStringLiteralExpression)firstArgument).getStringValue();
              if (calleeQNames.contains(TYPE_VAR_TUPLE) || calleeQNames.contains(TYPE_VAR_TUPLE_EXT)) {
                return new PyTypeVarTupleTypeImpl(name);
              }
              else {
                return new PyTypeVarTypeImpl(name, getGenericTypeBound(arguments, context));
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyTypeParameterType getTypeParameterTypeFromTypeParameter(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyTypeParameter typeParameter) {

      PyTypeParameterListOwner typeParameterOwner = PsiTreeUtil.getStubOrPsiParentOfType(element, PyTypeParameterListOwner.class);
      PyQualifiedNameOwner scopeOwner = typeParameterOwner instanceof PyQualifiedNameOwner qualifiedNameOwner ? qualifiedNameOwner : null;

      String boundExpressionText = typeParameter.getBoundExpressionText();
      String name = typeParameter.getName();
      PyTypeParameter.Kind kind = typeParameter.getKind();

      PyExpression boundExpression = boundExpressionText != null
                                     ? PyUtil.createExpressionFromFragment(boundExpressionText, typeParameter.getContainingFile())
                                     : null;

      if (name != null) {
        return switch (kind) {
          case TypeVar -> {
            PyType boundType = boundExpression != null ? getTypeParameterBoundType(boundExpression, context) : null;
            yield new PyTypeVarTypeImpl(name, boundType).withScopeOwner(scopeOwner);
          }
          case ParamSpec -> new PyParamSpecType(name).withScopeOwner(scopeOwner);
          case TypeVarTuple -> new PyTypeVarTupleTypeImpl(name).withScopeOwner(scopeOwner);
        };
      }
    }
    return null;
  }

  // See https://peps.python.org/pep-0484/#scoping-rules-for-type-variables
  @Nullable
  private static PyQualifiedNameOwner getTypeParameterScope(@NotNull String name, @NotNull PyExpression typeHint, @NotNull Context context) {
    PsiElement typeHintContext = getStubRetainedTypeHintContext(typeHint);
    // TODO: type aliases
    List<PyQualifiedNameOwner> typeParamOwnerCandidates =
      StreamEx.iterate(typeHintContext, Objects::nonNull, owner -> PsiTreeUtil.getStubOrPsiParentOfType(owner, ScopeOwner.class))
        .filter(owner -> owner instanceof PyFunction || owner instanceof PyClass)
        .select(PyQualifiedNameOwner.class)
        .toList();

    PyQualifiedNameOwner closestOwner = ContainerUtil.getFirstItem(typeParamOwnerCandidates);
    if (closestOwner instanceof PyClass) {
      return closestOwner;
    }
    return StreamEx.of(typeParamOwnerCandidates)
      .skip(1)
      .map(owner -> findSameTypeParameterInDefinition(owner, name, context))
      .nonNull()
      .findFirst()
      .map(PyTypeParameterType::getScopeOwner)
      .orElse(closestOwner);
  }

  private static @Nullable PyTypeParameterType findSameTypeParameterInDefinition(@NotNull PyQualifiedNameOwner owner,
                                                                                 @NotNull String name,
                                                                                 @NotNull Context context) {
    // At this moment, the definition of the TypeVar should be the type alias at the top of the stack.
    // While evaluating type hints of enclosing functions' parameters, resolving to the same TypeVar
    // definition shouldn't trigger the protection against recursive aliases, so we manually remove
    // it from the top for the time being.
    PyQualifiedNameOwner typeVarDeclaration = context.getTypeAliasStack().pop();
    assert typeVarDeclaration instanceof PyTargetExpression;
    try {
      if (owner instanceof PyClass) {
        return StreamEx.of(collectTypeParameters((PyClass)owner, context))
          .findFirst(type -> name.equals(type.getName()))
          .orElse(null);
      }
      else if (owner instanceof PyFunction) {
        return StreamEx.of(((PyFunction)owner).getParameterList().getParameters())
          .select(PyNamedParameter.class)
          .map(parameter -> new PyTypingTypeProvider().getParameterType(parameter, (PyFunction)owner, context))
          .map(Ref::deref)
          .map(paramType -> PyTypeChecker.collectGenerics(paramType, context.getTypeContext()))
          .flatMap(generics -> StreamEx.<PyTypeParameterType>of(generics.getTypeVars())
            .append(generics.getParamSpecs())
            .append(generics.getTypeVarTuples())
          )
          .findFirst(type -> name.equals(type.getName()))
          .orElse(null);
      }
    }
    finally {
      context.getTypeAliasStack().push(typeVarDeclaration);
    }
    return null;
  }

  @NotNull
  private static PsiElement getStubRetainedTypeHintContext(@NotNull PsiElement typeHintExpression) {
    // Values from PSI stubs and regular type comments
    PsiElement fragmentOwner = typeHintExpression.getContainingFile().getUserData(FRAGMENT_OWNER);
    if (fragmentOwner != null) {
      return fragmentOwner;
    }
    // Values from function type comments
    else if (typeHintExpression.getContainingFile() instanceof PyFunctionTypeAnnotationFile) {
      return PyPsiUtils.getRealContext(typeHintExpression);
    }
    else {
      return typeHintExpression;
    }
  }

  @Nullable
  public static PyVariadicType getUnpackedType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    Ref<@Nullable PyType> typeRef = getTypeFromStarExpression(element, context);
    if (typeRef == null) {
      typeRef = getTypeFromUnpackOperator(element, context);
    }
    if (typeRef == null) {
      return null;
    }
    var expressionType = typeRef.get();
    if (expressionType instanceof PyTupleType tupleType) {
      return new PyUnpackedTupleTypeImpl(tupleType.getElementTypes(), tupleType.isHomogeneous());
    }
    if (expressionType instanceof PyTypeVarTupleType typeVarTupleType) {
      return typeVarTupleType;
    }
    return null;
  }

  private static @Nullable Ref<@Nullable PyType> getTypeFromUnpackOperator(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (!(element instanceof PySubscriptionExpression subscriptionExpr) ||
        !resolvesToQualifiedNames(subscriptionExpr.getOperand(), context, UNPACK, UNPACK_EXT)) {
      return null;
    }
    PyExpression indexExpression = subscriptionExpr.getIndexExpression();
    if (!(indexExpression instanceof PyReferenceExpression || indexExpression instanceof PySubscriptionExpression)) return null;
    return Ref.create(Ref.deref(getType(indexExpression, context)));
  }

  private static @Nullable Ref<@Nullable PyType> getTypeFromStarExpression(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (!(element instanceof PyStarExpression starExpression)) return null;
    PyExpression starredExpression = starExpression.getExpression();
    if (!(starredExpression instanceof PyReferenceExpression || starredExpression instanceof PySubscriptionExpression)) return null;
    return Ref.create(Ref.deref(getType(starredExpression, context)));
  }

  @Nullable
  private static PyParamSpecType getParamSpecType(@NotNull PsiElement element, @NotNull Context context) {
    if (!(element instanceof PyCallExpression assignedCall)) return null;

    final var callee = assignedCall.getCallee();
    if (callee == null) return null;

    final var calleeQNames = resolveToQualifiedNames(callee, context.getTypeContext());
    if (!calleeQNames.contains(TYPING_PARAM_SPEC) && !calleeQNames.contains(TYPING_EXTENSIONS_PARAM_SPEC)) return null;

    final var arguments = assignedCall.getArguments();
    if (arguments.length == 0) return null;

    final var firstArgument = arguments[0];
    if (!(firstArgument instanceof PyStringLiteralExpression)) return null;

    final var name = ((PyStringLiteralExpression)firstArgument).getStringValue();
    return new PyParamSpecType(name);
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

  @Nullable
  private static PyType getTypeParameterBoundType(@NotNull PyExpression boundExpression, @NotNull Context context) {
    PyExpression bound = PyPsiUtils.flattenParens(boundExpression);
    if (bound != null) {
      if (bound instanceof PyTupleExpression tupleExpression) {
        return StreamEx.of(tupleExpression.getElements())
          .map(expr -> Ref.deref(getType(expr, context)))
          .collect(PyTypeUtil.toUnion());
      }
      else {
        return Ref.deref(getType(bound, context));
      }
    }
    return null;
  }

  @NotNull
  private static List<PyType> getIndexTypes(@NotNull PySubscriptionExpression expression, @NotNull Context context) {
    final List<PyType> types = new ArrayList<>();
    final PyExpression indexExpr = expression.getIndexExpression();
    if (indexExpr instanceof PyTupleExpression tupleExpr) {
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
    if (element instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();
      final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
      if (indexExpr != null) {
        final PyType operandType = Ref.deref(getType(operand, context));
        final List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
        if (operandType instanceof PyClassType && !(operandType instanceof PyTupleType) &&
            PyNames.TUPLE.equals(((PyClassType)operandType).getPyClass().getQualifiedName())) {
          if (indexExpr instanceof PyTupleExpression) {
            final PyExpression[] elements = ((PyTupleExpression)indexExpr).getElements();
            if (elements.length == 2 && isEllipsis(elements[1])) {
              return PyTupleType.createHomogeneous(element, indexTypes.get(0));
            }
          }
          return PyTupleType.create(element, indexTypes);
        }
        if (operandType != null) {
          return PyTypeChecker.parameterizeType(operandType, indexTypes, context.getTypeContext());
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
  private static List<Pair<PyQualifiedNameOwner, PsiElement>> tryResolvingWithAliases(@NotNull PyExpression expression,
                                                                                    @NotNull TypeEvalContext context) {
    final List<Pair<PyQualifiedNameOwner, PsiElement>> elements = new ArrayList<>();
    if (expression instanceof PyReferenceExpression) {
      final List<PsiElement> results;
      if (context.maySwitchToAST(expression)) {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
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
        if (element instanceof PyTargetExpression targetExpr) {
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
        if (element instanceof PyTypeAliasStatement typeAliasStatement) {
          PyExpression assignedValue;
          if (context.maySwitchToAST(typeAliasStatement)) {
            assignedValue = typeAliasStatement.getTypeExpression();
          }
          else {
            String assignedTypeText = typeAliasStatement.getTypeExpressionText();
            assignedValue = assignedTypeText != null ? toExpression(assignedTypeText, typeAliasStatement) : null;
          }
          if (assignedValue != null) {
            elements.add(Pair.create(typeAliasStatement, assignedValue));
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

    PsiElement anchor = expression.getContainingFile().getUserData(FRAGMENT_OWNER);
    ScopeOwner scopeOwner;

    if (anchor == null) {
      scopeOwner = pyFile;
    }
    else if (anchor instanceof ScopeOwner anchorAsScope) {
      scopeOwner = anchorAsScope;
    }
    else {
      scopeOwner = ScopeUtil.getScopeOwner(anchor);
    }

    if (scopeOwner != null && qualifiedName != null) {
      List<PsiElement> results = new ArrayList<>();
      while (scopeOwner != null) {
        results.addAll(PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context));
        scopeOwner = ScopeUtil.getScopeOwner(scopeOwner);
      }
      return results;
    }
    return Collections.singletonList(expression);
  }

  @NotNull
  public static Collection<String> resolveToQualifiedNames(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final Set<String> names = new LinkedHashSet<>();
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
    if (element instanceof PyQualifiedNameOwner qualifiedNameOwner) {
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
  public static PyType removeNarrowedTypeIfNeeded(@Nullable PyType type) {
    if (type instanceof PyNarrowedType pyNarrowedType) {
      return PyBuiltinCache.getInstance(pyNarrowedType.getOriginal()).getBoolType();
    }
    else {
      return type;
    }
  }

  @Nullable
  private static PyType wrapInCoroutineType(@Nullable PyType returnType, @NotNull PsiElement resolveAnchor) {
    final PyClass coroutine = PyPsiFacade.getInstance(resolveAnchor.getProject()).createClassByQName(COROUTINE, resolveAnchor);
    return coroutine != null ? new PyCollectionTypeImpl(coroutine, false, Arrays.asList(null, null, returnType)) : null;
  }

  @Nullable
  public static PyType wrapInGeneratorType(@Nullable PyType elementType, @Nullable PyType returnType, @NotNull PsiElement anchor) {
    final PyClass generator = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(GENERATOR, anchor);
    return generator != null ? new PyCollectionTypeImpl(generator, false, Arrays.asList(elementType, null, returnType)) : null;
  }

  @Nullable
  private static PyType wrapInAsyncGeneratorType(@Nullable PyType elementType, @NotNull PsiElement anchor) {
    final PyClass asyncGenerator = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(ASYNC_GENERATOR, anchor);
    return asyncGenerator != null ? new PyCollectionTypeImpl(asyncGenerator, false, Arrays.asList(elementType, null)) : null;
  }

  @Nullable
  public static Ref<PyType> coroutineOrGeneratorElementType(@Nullable PyType coroutineOrGeneratorType) {
    final PyCollectionType genericType = as(coroutineOrGeneratorType, PyCollectionType.class);
    final PyClassType classType = as(coroutineOrGeneratorType, PyClassType.class);

    if (genericType != null && classType != null) {
      var qName = classType.getClassQName();

      if ("typing.Awaitable".equals(qName)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 0, null));
      }

      if (ArrayUtil.contains(qName, COROUTINE, GENERATOR)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 2, null));
      }
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

  /**
   * Checks whether the given assignment is type hinted with {@code typing.TypeAlias}.
   * <p>
   * It can be done either with a variable annotation or a type comment.
   */
  public static boolean isExplicitTypeAlias(@NotNull PyAssignmentStatement assignment, @NotNull TypeEvalContext context) {
    PyExpression annotationValue = getAnnotationValue(assignment, context);
    if (annotationValue instanceof PyReferenceExpression) {
      Collection<String> qualifiedNames = resolveToQualifiedNames(annotationValue, context);
      return qualifiedNames.contains(TYPE_ALIAS) || qualifiedNames.contains(TYPE_ALIAS_EXT);
    }
    PyTargetExpression target = as(ArrayUtil.getFirstElement(assignment.getTargets()), PyTargetExpression.class);
    if (target != null) {
      String typeCommentAnnotation = target.getTypeCommentAnnotation();
      if (typeCommentAnnotation != null) {
        PyExpression commentValue = toExpression(typeCommentAnnotation, assignment);
        if (commentValue instanceof PyReferenceExpression) {
          Collection<String> qualifiedNames = resolveToQualifiedNames(commentValue, context);
          return qualifiedNames.contains(TYPE_ALIAS) || qualifiedNames.contains(TYPE_ALIAS_EXT);
        }
      }
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

  /**
   * Detects whether the given element belongs to a self-evident type hint. Namely, these are:
   * <ul>
   *   <li>function and variable annotations</li>
   *   <li>type comments</li>
   *   <li>explicit type aliases marked with {@code TypeAlias}</li>
   * </ul>
   * Note that {@code element} can belong to their AST directly or be a part of an injection inside one of such elements.
   */
  public static boolean isInsideTypeHint(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final PsiElement realContext = PyPsiUtils.getRealContext(element);

    if (PsiTreeUtil.getParentOfType(realContext, PyAnnotation.class, false, PyStatement.class) != null) {
      return true;
    }

    final PsiComment comment = PsiTreeUtil.getParentOfType(realContext, PsiComment.class, false, PyStatement.class);
    if (comment != null && getTypeCommentValue(comment.getText()) != null) {
      return true;
    }

    PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(realContext, PyAssignmentStatement.class, false, PyStatement.class);
    if (assignment != null &&
        PsiTreeUtil.isAncestor(assignment.getAssignedValue(), realContext, false) &&
        isExplicitTypeAlias(assignment, context)) {
      return true;
    }

    PyTypeAliasStatement typeAlias = PsiTreeUtil.getParentOfType(realContext, PyTypeAliasStatement.class, false, PyStatement.class);
    if (typeAlias != null && PsiTreeUtil.isAncestor(typeAlias.getTypeExpression(), realContext, false)) {
      return true;
    }

    return false;
  }

  @Override
  protected <T> T withCustomContext(@NotNull TypeEvalContext context, @NotNull Function<@NotNull Context, T> delegate) {
    return staticWithCustomContext(context, delegate);
  }

  private static <T> T staticWithCustomContext(@NotNull TypeEvalContext context, @NotNull Function<@NotNull Context, T> delegate) {
    Context customContext = context.getProcessingContext().get(TYPE_HINT_EVAL_CONTEXT);
    boolean firstEntrance = customContext == null;
    if (firstEntrance) {
      customContext = new Context(context);
      context.getProcessingContext().put(TYPE_HINT_EVAL_CONTEXT, customContext);
    }
    try {
      return delegate.apply(customContext);
    }
    finally {
      if (firstEntrance) {
        context.getProcessingContext().put(TYPE_HINT_EVAL_CONTEXT, null);
      }
    }
  }

  static class Context {
    @NotNull private final TypeEvalContext myContext;
    @NotNull private final Stack<PyQualifiedNameOwner> myTypeAliasStack = new Stack<>();

    private Context(@NotNull TypeEvalContext context) {
      myContext = context;
    }

    @NotNull
    public TypeEvalContext getTypeContext() {
      return myContext;
    }

    @NotNull
    public Stack<PyQualifiedNameOwner> getTypeAliasStack() {
      return myTypeAliasStack;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Context context = (Context)o;
      return Objects.equals(myContext, context.myContext);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myContext);
    }
  }

  public enum TypeGuardKind {
    TypeGuard,
    TypeIs,
    None
  }
}
