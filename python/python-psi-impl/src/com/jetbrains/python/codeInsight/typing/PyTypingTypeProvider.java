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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyEvaluator;
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
import java.util.stream.Stream;

import static com.intellij.openapi.util.RecursionManager.doPreventingRecursion;
import static com.jetbrains.python.psi.PyKnownDecorator.TYPING_FINAL;
import static com.jetbrains.python.psi.PyKnownDecorator.TYPING_FINAL_EXT;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

public final class PyTypingTypeProvider extends PyTypeProviderWithCustomContext<PyTypingTypeProvider.Context> {

  public static final @NlsSafe String TYPING = "typing";

  public static final String GENERATOR = "typing.Generator";
  public static final String ASYNC_GENERATOR = "typing.AsyncGenerator";
  public static final String COROUTINE = "typing.Coroutine";
  public static final String AWAITABLE = "typing.Awaitable";
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
  public static final String TYPE_VAR_EXT = "typing_extensions.TypeVar";
  public static final String TYPE_VAR_TUPLE = "typing.TypeVarTuple";
  public static final String TYPE_VAR_TUPLE_EXT = "typing_extensions.TypeVarTuple";
  public static final String PARAM_SPEC = "typing.ParamSpec";
  public static final String PARAM_SPEC_EXT = "typing_extensions.ParamSpec";
  private static final String CHAIN_MAP = "typing.ChainMap";
  public static final String UNION = "typing.Union";
  public static final String CONCATENATE = "typing.Concatenate";
  public static final String CONCATENATE_EXT = "typing_extensions.Concatenate";
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

  public static final Set<String> TYPE_PARAMETER_FACTORIES = Set.of(
    TYPE_VAR, TYPE_VAR_EXT,
    PARAM_SPEC, PARAM_SPEC_EXT,
    TYPE_VAR_TUPLE, TYPE_VAR_TUPLE_EXT
  );

  public static final Set<String> TYPE_DICT_QUALIFIERS =
    Set.of(REQUIRED, REQUIRED_EXT, NOT_REQUIRED, NOT_REQUIRED_EXT, READONLY, READONLY_EXT);

  public static final String UNPACK = "typing.Unpack";
  public static final String UNPACK_EXT = "typing_extensions.Unpack";

  public static final String SELF = "typing.Self";
  public static final String SELF_EXT = "typing_extensions.Self";

  public static final Pattern TYPE_IGNORE_PATTERN =
    Pattern.compile("#\\s*type:\\s*ignore\\s*(\\[[^]#]*])?($|(\\s.*))", Pattern.CASE_INSENSITIVE);

  public static final String ASSERT_TYPE = "typing.assert_type";
  public static final String REVEAL_TYPE = "typing.reveal_type";
  public static final String REVEAL_TYPE_EXT = "typing_extensions.reveal_type";
  public static final String CAST = "typing.cast";
  public static final String CAST_EXT = "typing_extensions.cast";

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

  public static final ImmutableMap<String, @NlsSafe String> TYPING_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
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
    .add(TUPLE, GENERIC, PROTOCOL, CALLABLE, TYPE, CLASS_VAR, FINAL, LITERAL, ANNOTATED, REQUIRED, NOT_REQUIRED, READONLY)
    // type aliases
    .add(UNION, OPTIONAL, LIST, DICT, DEFAULT_DICT, ORDERED_DICT, SET, FROZEN_SET, COUNTER, DEQUE, CHAIN_MAP)
    .add(PROTOCOL_EXT, FINAL_EXT, LITERAL_EXT, ANNOTATED_EXT, REQUIRED_EXT, NOT_REQUIRED_EXT, READONLY_EXT)
    .build();

  /**
   * For the following names we shouldn't go further to the RHS of assignments,
   * since they are not type aliases already and in typing.pyi are assigned to
   * some synthetic values.
   */
  public static final ImmutableSet<String> OPAQUE_NAMES = ImmutableSet.<String>builder()
    .add(PyKnownDecorator.TYPING_OVERLOAD.getQualifiedName().toString())
    .add(ANY)
    .add(TYPE_VAR)
    .add(TYPE_VAR_EXT)
    .add(TYPE_VAR_TUPLE)
    .add(TYPE_VAR_TUPLE_EXT)
    .add(GENERIC)
    .add(PARAM_SPEC)
    .add(PARAM_SPEC_EXT)
    .add(CONCATENATE)
    .add(CONCATENATE_EXT)
    .add(TUPLE)
    .add(CALLABLE)
    .add(TYPE)
    .add(PyKnownDecorator.TYPING_NO_TYPE_CHECK.getQualifiedName().toString())
    .add(PyKnownDecorator.TYPING_NO_TYPE_CHECK_EXT.getQualifiedName().toString())
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
    .add(NO_RETURN, NO_RETURN_EXT)
    .add(NEVER, NEVER_EXT)
    .add(FINAL, FINAL_EXT)
    .add(LITERAL, LITERAL_EXT)
    .add(TYPED_DICT, TYPED_DICT_EXT)
    .add(ANNOTATED, ANNOTATED_EXT)
    .add(TYPE_ALIAS, TYPE_ALIAS_EXT)
    .add(REQUIRED, REQUIRED_EXT)
    .add(NOT_REQUIRED, NOT_REQUIRED_EXT)
    .add(READONLY, READONLY_EXT)
    .add(SELF, SELF_EXT)
    .add(LITERALSTRING, LITERALSTRING_EXT)
    .build();

  // Type hints in PSI stubs, type comments and "escaped" string annotations are represented as PyExpressionCodeFragments
  // created from the corresponding text fragments. This key points to the closest definition in the original file
  // they belong to.
  // TODO Make this the result of PyExpressionCodeFragment.getContext()
  private static final Key<PsiElement> FRAGMENT_OWNER = Key.create("PY_FRAGMENT_OWNER");
  private static final Key<Context> TYPE_HINT_EVAL_CONTEXT = Key.create("TYPE_HINT_EVAL_CONTEXT");

  @Override
  public @Nullable PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull Context context) {
    // Check for the exact name in advance for performance reasons
    if ("Generic".equals(referenceExpression.getName())) {
      if (resolveToQualifiedNames(referenceExpression, context.myContext).contains(GENERIC)) {
        return createTypingGenericType(referenceExpression);
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Protocol".equals(referenceExpression.getName())) {
      if (ContainerUtil.exists(resolveToQualifiedNames(referenceExpression, context.myContext),
                               n -> PROTOCOL.equals(n) || PROTOCOL_EXT.equals(n))) {
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
  public @Nullable Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull Context context) {
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
      return Ref.create(PyUnionType.union(type, PyBuiltinCache.getInstance(param).getNoneType()));
    }
    return Ref.create(type);
  }

  private static @Nullable PyExpression findParamTypeHintInFunctionTypeComment(@NotNull PyFunctionTypeAnnotation annotation,
                                                                               @NotNull PyNamedParameter param,
                                                                               @NotNull PyFunction func) {
    List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
    if (paramTypes.size() == 1 && paramTypes.get(0) instanceof PyEllipsisLiteralExpression) {
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

  private static @NotNull PyType createTypingGenericType(@NotNull PsiElement anchor) {
    return new PyCustomType(GENERIC, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
  }

  private static @NotNull PyType createTypingProtocolType(@NotNull PsiElement anchor) {
    return new PyCustomType(PROTOCOL, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
  }

  public static @NotNull PyType createTypingCallableType(@NotNull PsiElement anchor) {
    return new PyCustomType(CALLABLE, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
  }

  private static boolean omitFirstParamInTypeComment(@NotNull PyFunction func, @NotNull PyFunctionTypeAnnotation annotation) {
    return func.getContainingClass() != null && func.getModifier() != PyAstFunction.Modifier.STATICMETHOD &&
           annotation.getParameterTypeList().getParameterTypes().size() < func.getParameterList().getParameters().length;
  }

  @Override
  public @Nullable Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull Context context) {
    if (callable instanceof PyFunction function) {
      final PyExpression returnTypeAnnotation = getReturnTypeAnnotation(function, context.myContext);
      if (returnTypeAnnotation != null) {
        final Ref<PyType> typeRef = getType(returnTypeAnnotation, context);
        if (typeRef != null) {
          // Do not use toAsyncIfNeeded, as it also converts Generators. Here we do not need it.
          if (function.isAsync() && function.isAsyncAllowed() && !function.isGenerator()) {
            return Ref.create(wrapInCoroutineType(typeRef.get(), function));
          }
          return typeRef;
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

  public static @Nullable PyFunctionTypeAnnotation getFunctionTypeAnnotation(@NotNull PyFunction function) {
    final String comment = function.getTypeCommentAnnotation();
    if (comment == null) {
      return null;
    }
    final PyFunctionTypeAnnotationFile file = CachedValuesManager.getCachedValue(function, () ->
      CachedValueProvider.Result.create(new PyFunctionTypeAnnotationFile(function.getTypeCommentAnnotation(), function), function));
    return file.getAnnotation();
  }

  @Override
  public @Nullable Ref<PyType> getCallType(@NotNull PyFunction function, @NotNull PyCallSiteExpression callSite, @NotNull Context context) {
    final String functionQName = function.getQualifiedName();

    if (CAST.equals(functionQName) || CAST_EXT.equals(functionQName)) {
      return Optional
        .ofNullable(as(callSite, PyCallExpression.class))
        .map(PyCallExpression::getArguments)
        .filter(args -> args.length > 0)
        .map(args -> getType(args[0], context))
        .orElse(null);
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

  private static @Nullable PyType getTypedDictTypeForTarget(@NotNull PyTargetExpression referenceTarget, @NotNull TypeEvalContext context) {
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

      PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(target);
      if (assignedValue instanceof PyCallExpression callExpression && callExpression.isCalleeText("TypeVar", "TypeVarTuple", "ParamSpec")) {
        for (PsiElement element : tryResolving(Objects.requireNonNull(callExpression.getCallee()), context.myContext)) {
          if (element instanceof PyClass pyClass &&
              TYPE_PARAMETER_FACTORIES.contains(ObjectUtils.notNull(pyClass.getQualifiedName(), "")) &&
              context.myContext.getType(pyClass) instanceof PyClassType pyClassType) {
            return Ref.create(pyClassType.toInstance());
          }
        }
      }

      // Return a type from an immediate type hint, e.g. from a syntactic annotation for
      // 
      // x: int = ...
      //
      // or find the "root" declaration and get a type from a type hint there, e.g.
      // 
      // x: int
      // x = ...
      //
      // or
      //
      // class C:
      //     attr: int
      //     def __init__(self, x):
      //         self.attr = x
      //
      // self.attr = ...

      // assignments "inst.attr = ..." are not preserved in stubs anyway. See PyTargetExpressionElementType.shouldCreateStub.
      if (target.isQualified() && context.myContext.maySwitchToAST(target)) {
        PsiElement resolved = target.getReference(PyResolveContext.defaultContext(context.myContext)).resolve();
        if (resolved instanceof PyTargetExpression resolvedTarget && PyUtil.isAttribute(resolvedTarget)) {
          target = resolvedTarget;
        }
      }

      final Ref<PyType> annotatedType = getTypeFromTargetExpressionAnnotation(target, context);
      if (annotatedType != null) {
        return annotatedType;
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
          Ref<PyType> memberType =
            getMemberTypeForClassType(context, target, name, resolveContext, false, new PyClassTypeImpl(pyClass, true));
          if (memberType != null) {
            return memberType;
          }

          for (PyClass ancestor : pyClass.getAncestorClasses(resolveContext.getTypeEvalContext())) {
            Ref<PyType> ancestorMemberType =
              getMemberTypeForClassType(context, target, name, resolveContext, true, new PyClassTypeImpl(ancestor, false));
            if (ancestorMemberType != null) {
              return ancestorMemberType;
            }
          }

          return null;
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
    if (anchor instanceof PyExpression && referenceTarget instanceof PyClass pyClass && isGeneric(pyClass, context.myContext)) {
      PyCollectionType parameterizedType = parameterizeClassDefaultAware(pyClass, List.of(), context);
      if (parameterizedType != null) {
        return Ref.create(parameterizedType.toClass());
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getMemberTypeForClassType(@NotNull Context context,
                                                                 PyTargetExpression target,
                                                                 String name,
                                                                 PyResolveContext resolveContext,
                                                                 boolean isInherited,
                                                                 PyClassTypeImpl classType) {
    final List<? extends RatedResolveResult> classAttrs =
      classType.resolveMember(name, target, AccessDirection.READ, resolveContext, isInherited);
    if (classAttrs == null) {
      return null;
    }
    return StreamEx.of(classAttrs)
      .map(RatedResolveResult::getElement)
      .select(PyTargetExpression.class)
      .filter(x -> {
        ScopeOwner owner = ScopeUtil.getScopeOwner(x);
        return owner instanceof PyClass || owner instanceof PyFunction;
      })
      .map(x -> getTypeFromTargetExpressionAnnotation(x, context))
      .collect(PyTypeUtil.toUnionFromRef());
  }

  private static @Nullable Ref<PyType> getTypeFromTargetExpressionAnnotation(@NotNull PyTargetExpression target, @NotNull Context context) {
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
  public static @Nullable String getTypeCommentValue(@NotNull String text) {
    return PyUtilCore.getTypeCommentValue(text);
  }

  /**
   * Returns the corresponding text range for a type hint as returned by {@link #getTypeCommentValue(String)}.
   *
   * @see #getTypeCommentValue(String)
   */
  public static @Nullable TextRange getTypeCommentValueRange(@NotNull String text) {
    return PyUtilCore.getTypeCommentValueRange(text);
  }

  @Override
  public @Nullable PyType getGenericType(@NotNull PyClass cls, @NotNull Context context) {
    List<PyTypeParameterType> typeParameters = collectTypeParameters(cls, context);
    return typeParameters.isEmpty() ? null : new PyCollectionTypeImpl(cls, false, typeParameters);
  }

  @Override
  public @NotNull Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull Context context) {
    return PyUtil.getParameterizedCachedValue(cls, context, c -> calculateGenericSubstitutions(cls, c));
  }

  private @NotNull Map<PyType, PyType> calculateGenericSubstitutions(@NotNull PyClass cls, @NotNull Context context) {
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
        PyTypeParameterMapping.mapByShape(superTypeParameters, superTypeArguments, Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
                                          Option.USE_DEFAULTS);
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

  private static @NotNull List<PyTypeParameterType> collectTypeParameters(@NotNull PyClass cls, @NotNull Context context) {
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
        return StreamEx.<PyType>of(typeParams.getTypeVars()).append(typeParams.getTypeVarTuples())
          .append(StreamEx.of(typeParams.getParamSpecs()));
      })
      .select(PyTypeParameterType.class)
      .distinct()
      .toList();
  }

  private static @NotNull List<PyTypeParameterType> collectTypeParametersFromTypeAliasStatement(@NotNull PyTypeAliasStatement typeAliasStatement,
                                                                                                @NotNull Context context) {
    PyTypeParameterList typeParameterList = typeAliasStatement.getTypeParameterList();
    if (typeParameterList != null) {
      List<PyTypeParameter> typeParameters = typeParameterList.getTypeParameters();
      return StreamEx.of(typeParameters)
        .map(typeParameter -> getTypeParameterTypeFromTypeParameter(typeParameter, context))
        .nonNull()
        .toList();
    }
    return Collections.emptyList();
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

  public static @Nullable Ref<PyType> getType(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    return staticWithCustomContext(context, customContext -> getType(expression, customContext));
  }

  private static @Nullable Ref<PyType> getType(@NotNull PyExpression expression, @NotNull Context context) {
    for (Pair<PyQualifiedNameOwner, PsiElement> pair : tryResolvingWithAliases(expression, context.getTypeContext())) {
      final Ref<PyType> typeRef = getTypeForResolvedElement(expression, pair.getFirst(), pair.getSecond(), context);
      if (typeRef != null) {
        return typeRef;
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getTypeFromBitwiseOrOperator(@NotNull PyBinaryExpression expression, @NotNull Context context) {
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

  private static @Nullable Ref<PyType> getTypeForResolvedElement(@NotNull PyExpression typeHint,
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
      final Ref<PyType> typeHintFromProvider = PyTypeHintProvider.Companion.parseTypeHint(
        typeHint,
        alias,
        resolved,
        context.getTypeContext()
      );
      if (typeHintFromProvider != null) {
        return typeHintFromProvider;
      }
      final Ref<PyType> typeFromParenthesizedExpression = getTypeFromParenthesizedExpression(resolved, context);
      if (typeFromParenthesizedExpression != null) {
        return typeFromParenthesizedExpression;
      }
      // We perform chained resolve only for actual aliases as tryResolvingWithAliases() returns the passed-in
      // expression both when it's not a reference expression and when it's failed to resolve it, hence we might
      // hit SOE for mere unresolved references in the latter case.
      if (alias != null) {
        Ref<PyType> typeFromTypeAlias = getTypeFromTypeAlias(alias, typeHint, resolved, context);
        if (typeFromTypeAlias != null) {
          return typeFromTypeAlias;
        }
      }
      final PyType neverType = getNeverType(resolved);
      if (neverType != null) {
        return Ref.create(neverType);
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
      final Ref<PyType> requiredOrNotRequiredType = getTypedDictSpecialItemType(resolved, context);
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
      final Ref<PyType> narrowedType = getNarrowedType(resolved, context);
      if (narrowedType != null) {
        return narrowedType;
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
      final PyType callableParameterListType = getCallableParameterListType(resolved, context);
      if (callableParameterListType != null) {
        return Ref.create(callableParameterListType);
      }
      final PyType stringBasedType = getStringLiteralType(resolved, context);
      if (stringBasedType != null) {
        return Ref.create(stringBasedType);
      }
      final Ref<PyType> anyType = getAnyType(resolved);
      if (anyType != null) {
        return anyType;
      }
      final PyType typedDictType = PyTypedDictTypeProvider.Companion.getTypedDictTypeForResolvedElement(resolved, context.getTypeContext());
      if (typedDictType != null) {
        return Ref.create(typedDictType);
      }
      final Ref<PyType> classType = getClassType(typeHint, resolved, context);
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

  private static @Nullable PyType getCallableParameterListType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyListLiteralExpression listLiteral) {
      List<PyType> argumentTypes = ContainerUtil.map(listLiteral.getElements(), defExpr -> Ref.deref(getType(defExpr, context)));
      return new PyCallableParameterListTypeImpl(ContainerUtil.map(argumentTypes, PyCallableParameterImpl::nonPsi));
    }
    return null;
  }

  private static @Nullable Ref<PyType> getNarrowedType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {
      Collection<String> names = resolveToQualifiedNames(subscriptionExpr.getOperand(), context.getTypeContext());
      var isTypeIs = names.contains(TYPE_IS) || names.contains(TYPE_IS_EXT);
      var isTypeGuard = names.contains(TYPE_GUARD) || names.contains(TYPE_GUARD_EXT);
      if (isTypeIs || isTypeGuard) {
        List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
        if (indexTypes.size() == 1) {
          PyNarrowedType narrowedType = PyNarrowedType.Companion.create(subscriptionExpr, isTypeIs, indexTypes.get(0));
          if (narrowedType != null) {
            return Ref.create(narrowedType);
          }
        }
      }
    }
    return null;
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

  private static @Nullable Ref<PyType> getTypeFromBinaryExpression(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyBinaryExpression) {
      return getTypeFromBitwiseOrOperator((PyBinaryExpression)resolved, context);
    }
    return null;
  }

  private static @Nullable Ref<PyType> getTypeFromParenthesizedExpression(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyParenthesizedExpression) {
      final PyExpression containedExpression = PyPsiUtils.flattenParens((PyExpression)resolved);
      return containedExpression != null ? getType(containedExpression, context) : null;
    }
    return null;
  }

  private static @Nullable Ref<PyType> getExplicitTypeAliasType(@NotNull PsiElement resolved) {
    if (resolved instanceof PyQualifiedNameOwner) {
      String qualifiedName = ((PyQualifiedNameOwner)resolved).getQualifiedName();
      if (TYPE_ALIAS.equals(qualifiedName) || TYPE_ALIAS_EXT.equals(qualifiedName)) {
        return Ref.create();
      }
    }
    return null;
  }

  private static @Nullable PyType anchorTypeParameter(@NotNull PyExpression typeHint, @Nullable PyType type, @NotNull Context context) {
    PyQualifiedNameOwner typeParamDefinitionFromStack = context.getTypeAliasStack().isEmpty() ? null : context.getTypeAliasStack().peek();
    assert typeParamDefinitionFromStack == null || typeParamDefinitionFromStack instanceof PyTargetExpression;
    PyTargetExpression targetExpr = (PyTargetExpression)typeParamDefinitionFromStack;
    if (type instanceof PyTypeVarTypeImpl typeVar) {
      return typeVar.withScopeOwner(getTypeParameterScope(typeVar.getName(), typeHint, context)).withDeclarationElement(targetExpr);
    }
    if (type instanceof PyParamSpecType paramSpec) {
      return paramSpec.withScopeOwner(getTypeParameterScope(paramSpec.getName(), typeHint, context)).withDeclarationElement(targetExpr);
    }
    if (type instanceof PyTypeVarTupleTypeImpl typeVarTuple) {
      return typeVarTuple.withScopeOwner(getTypeParameterScope(typeVarTuple.getName(), typeHint, context))
        .withDeclarationElement(targetExpr);
    }
    return type;
  }

  private static @Nullable Ref<PyType> getClassObjectType(@NotNull PsiElement resolved, @NotNull Context context) {
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

  private static @NotNull Ref<PyType> getAsClassObjectType(@NotNull PyExpression expression, @NotNull Context context) {
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

  private static @Nullable Ref<PyType> getAnyType(@NotNull PsiElement element) {
    return ANY.equals(getQualifiedName(element)) ? Ref.create() : null;
  }

  private static @Nullable Ref<PyType> getClassType(@NotNull PyExpression typeHint, @NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyTypedElement) {
      TypeEvalContext typeContext = context.getTypeContext();
      final PyType type = typeContext.getType((PyTypedElement)element);
      if (type instanceof PyClassLikeType classLikeType) {
        if (classLikeType.isDefinition()) {
          // If we're interpreting a type hint like "MyGeneric" that is not followed by a list of type arguments (e.g. MyGeneric[int]),
          // we want to parameterize it with its type parameters defaults already here.
          // We need this check for the type argument list because getParameterizedType() relies on getClassType() for
          // getting the type corresponding to the subscription expression operand.
          if (classLikeType instanceof PyClassType classType &&
              isGeneric(classLikeType, typeContext) &&
              !(typeHint.getParent() instanceof PySubscriptionExpression se && typeHint.equals(se.getOperand()))) {
            PyCollectionType parameterized = parameterizeClassDefaultAware(classType.getPyClass(), List.of(), context);
            if (parameterized != null) {
              return Ref.create(parameterized.toInstance());
            }
          }
          final PyType instanceType = classLikeType.toInstance();
          return Ref.create(instanceType);
        }
        else if (isNoneType(classLikeType)) {
          final PyType instanceType = classLikeType.toInstance();
          return Ref.create(instanceType);
        }
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getOptionalType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(OPTIONAL)) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        if (indexExpr != null) {
          final Ref<PyType> typeRef = getType(indexExpr, context);
          if (typeRef != null) {
            return Ref.create(PyUnionType.union(typeRef.get(), PyBuiltinCache.getInstance(element).getNoneType()));
          }
        }
        return Ref.create();
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getLiteralStringType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PyTargetExpression referenceExpression) {
      Collection<String> operandNames = resolveToQualifiedNames(referenceExpression, context.getTypeContext());
      if (ContainerUtil.exists(operandNames, name -> name.equals(LITERALSTRING) || name.equals(LITERALSTRING_EXT))) {
        return Ref.create(PyLiteralStringType.Companion.create(resolved));
      }
    }

    return null;
  }

  private static @Nullable Ref<PyType> getLiteralType(@NotNull PsiElement resolved, @NotNull Context context) {
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

  private static @Nullable Ref<PyType> getAnnotatedType(@NotNull PsiElement resolved, @NotNull Context context) {
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

  private static @Nullable Ref<PyType> getTypedDictSpecialItemType(@NotNull PsiElement resolved, @NotNull Context context) {
    if (resolved instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();

      Collection<String> resolvedNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (ContainerUtil.exists(resolvedNames, name -> REQUIRED.equals(name) || REQUIRED_EXT.equals(name) ||
                                                      NOT_REQUIRED.equals(name) || NOT_REQUIRED_EXT.equals(name) ||
                                                      READONLY.equals(name) || READONLY_EXT.equals(name))) {
        final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
        final PyExpression type = indexExpr instanceof PyTupleExpression ? ((PyTupleExpression)indexExpr).getElements()[0] : indexExpr;
        if (type != null) {
          return getType(type, context);
        }
      }
    }

    return null;
  }

  private static @Nullable Ref<PyType> unwrapTypeModifier(@NotNull PsiElement resolved, @NotNull Context context, String... type) {
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

  private static @Nullable PyExpression toExpression(@NotNull String contents, @NotNull PsiElement anchor) {
    final PsiFile file = FileContextUtil.getContextFile(anchor);
    if (file == null) return null;
    PyExpression fragment = PyUtil.createExpressionFromFragment(contents, file);
    if (fragment != null) {
      fragment.getContainingFile().putUserData(FRAGMENT_OWNER, anchor);
    }
    return fragment;
  }

  public static @Nullable Ref<PyType> getStringBasedType(@NotNull String contents,
                                                         @NotNull PsiElement anchor,
                                                         @NotNull TypeEvalContext context) {
    return staticWithCustomContext(context, c -> getStringBasedType(contents, anchor, c));
  }

  private static @Nullable Ref<PyType> getStringBasedType(@NotNull String contents, @NotNull PsiElement anchor, @NotNull Context context) {
    final PyExpression expr = toExpression(contents, anchor);
    return expr != null ? getType(expr, context) : null;
  }

  private static @Nullable PyType getStringLiteralType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyStringLiteralExpression) {
      final String contents = ((PyStringLiteralExpression)element).getStringValue();
      return Ref.deref(getStringBasedType(contents, element, context));
    }
    return null;
  }

  private static @Nullable Ref<PyType> getVariableTypeCommentType(@NotNull String contents,
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

  private static @Nullable PyExpression findTopmostTarget(@NotNull PyTargetExpression target) {
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

  public static @NotNull Map<PyTargetExpression, PyExpression> mapTargetsToAnnotations(@NotNull PyExpression targetExpr,
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

  private static @Nullable PyType getCallableType(@NotNull PsiElement resolved, @NotNull Context context) {
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
            PyType returnType = Ref.deref(getType(returnTypeExpr, context));
            if (returnType instanceof PyVariadicType) {
              returnType = null;
            }
            if (parametersExpr instanceof PyEllipsisLiteralExpression) {
              return new PyCallableTypeImpl(null, returnType);
            }
            PyType parametersType = Ref.deref(getType(parametersExpr, context));
            if (parametersType instanceof PyCallableParameterListType paramList) {
              return new PyCallableTypeImpl(paramList.getParameters(), returnType);
            }
            if (parametersType instanceof PyParamSpecType || parametersType instanceof PyConcatenateType) {
              PyCallableParameter paramSpecParam = PyCallableParameterImpl.nonPsi(parametersType);
              return new PyCallableTypeImpl(Collections.singletonList(paramSpecParam), returnType);
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

  private static @Nullable PyType getNeverType(@NotNull PsiElement element) {
    var qName = getQualifiedName(element);
    if (qName == null) return null;
    if (List.of(NEVER, NEVER_EXT).contains(qName)) {
      return PyNeverType.NEVER;
    }
    if (List.of(NO_RETURN, NO_RETURN_EXT).contains(qName)) {
      return PyNeverType.NO_RETURN;
    }
    return null;
  }

  private static @Nullable PyType getUnionType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();
      final Collection<String> operandNames = resolveToQualifiedNames(operand, context.getTypeContext());
      if (operandNames.contains(UNION)) {
        return PyUnionType.union(getIndexTypes(subscriptionExpr, context));
      }
    }
    return null;
  }

  private static @Nullable PyType getConcatenateType(@NotNull PsiElement element, @NotNull Context context) {
    if (!(element instanceof PySubscriptionExpression subscriptionExpr)) return null;

    final var operand = subscriptionExpr.getOperand();
    final var operandNames = resolveToQualifiedNames(operand, context.myContext);
    if (!operandNames.contains(CONCATENATE) && !operandNames.contains(CONCATENATE_EXT)) return null;

    final var parameters = getConcatenateParametersTypes(subscriptionExpr, context.myContext);
    if (parameters == null) return null;

    return new PyConcatenateType(parameters.first, parameters.second);
  }

  private static @Nullable Pair<List<PyType>, PyParamSpecType> getConcatenateParametersTypes(@NotNull PySubscriptionExpression subscriptionExpression,
                                                                                             @NotNull TypeEvalContext context) {
    final var tuple = subscriptionExpression.getIndexExpression();
    if (!(tuple instanceof PyTupleExpression tupleExpression)) return null;
    final var result = ContainerUtil.map(tupleExpression.getElements(),
                                         it -> Ref.deref(getType(it, context)));
    if (result.size() < 2) return null;
    PyType lastParameter = result.get(result.size() - 1);
    if (!(lastParameter instanceof PyParamSpecType paramSpecType)) return null;
    return new Pair<>(result.subList(0, result.size() - 1), paramSpecType);
  }

  private static @Nullable PyTypeParameterType getTypeParameterTypeFromDeclaration(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PyCallExpression assignedCall) {
      PyAstTypeParameter.Kind typeParameterKind = getTypeParameterKindFromDeclaration(assignedCall, context.getTypeContext());
      if (typeParameterKind != null) {
        final PyExpression[] arguments = assignedCall.getArguments();
        if (arguments.length > 0 && arguments[0] instanceof PyStringLiteralExpression nameArgument) {
          final String name = nameArgument.getStringValue();
          PyExpression defaultExpression = assignedCall.getKeywordArgument("default");
          Ref<PyType> defaultType = defaultExpression != null ? getType(defaultExpression, context) : null;
          switch (typeParameterKind) {
            case TypeVarTuple -> {
              return new PyTypeVarTupleTypeImpl(name)
                .withDefaultType(defaultType != null && Ref.deref(defaultType) instanceof PyPositionalVariadicType posVariadic
                                 ? Ref.create(posVariadic) : null);
            }
            case TypeVar -> {
              // TypeVar __init__ parameters:
              // (name, *constraints, bound = None, contravariant = False, covariant = False, infer_variance = False, default = ...)
              List<PyType> constraints = Stream.of(arguments)
                .skip(1)
                .takeWhile(expr -> !(expr instanceof PyKeywordArgument))
                .map(expr -> Ref.deref(getType(expr, context)))
                .toList();
              PyExpression boundExpression = assignedCall.getKeywordArgument("bound");
              PyType bound = boundExpression == null ? null : Ref.deref(getType(boundExpression, context));
              PyTypeVarType.Variance variance = getTypeVarVarianceFromDeclaration(assignedCall);
              return new PyTypeVarTypeImpl(name, constraints, bound, defaultType, variance);
            }
            case ParamSpec -> {
              return new PyParamSpecType(name)
                .withDefaultType(defaultType != null && Ref.deref(defaultType) instanceof PyCallableParameterVariadicType paramVariadic
                                 ? Ref.create(paramVariadic) : null);
            }
          }
        }
      }
    }
    return null;
  }

  private static @NotNull PyTypeVarType.Variance getTypeVarVarianceFromDeclaration(@NotNull PyCallExpression assignedCall) {
    boolean covariant = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("covariant"), false);
    boolean contravariant = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("contravariant"), false);
    boolean inferVariance = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("infer_variance"), false);

    if (covariant && !contravariant) {
      return PyTypeVarType.Variance.COVARIANT;
    }
    else if (contravariant && !covariant) {
      return PyTypeVarType.Variance.CONTRAVARIANT;
    }
    else if (inferVariance) {
      return PyTypeVarType.Variance.INFER_VARIANCE;
    }
    else {
      return PyTypeVarType.Variance.INVARIANT;
    }
  }

  @ApiStatus.Internal
  public static @Nullable PyAstTypeParameter.Kind getTypeParameterKindFromDeclaration(@NotNull PyCallExpression callExpression,
                                                                                      @NotNull TypeEvalContext context) {
    final PyExpression callee = callExpression.getCallee();
    if (callee != null) {
      final Collection<String> calleeQNames = resolveToQualifiedNames(callee, context);
      if (calleeQNames.contains(TYPE_VAR_TUPLE) || calleeQNames.contains(TYPE_VAR_TUPLE_EXT)) return PyAstTypeParameter.Kind.TypeVarTuple;
      if (calleeQNames.contains(TYPE_VAR) || calleeQNames.contains(TYPE_VAR_EXT)) return PyAstTypeParameter.Kind.TypeVar;
      if (calleeQNames.contains(PARAM_SPEC) || calleeQNames.contains(PARAM_SPEC_EXT)) return PyAstTypeParameter.Kind.ParamSpec;
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable PyTypeParameterType getTypeParameterTypeFromTypeParameter(@NotNull PyTypeParameter typeParameter,
                                                                                    @NotNull TypeEvalContext context) {
    return staticWithCustomContext(context, c -> getTypeParameterTypeFromTypeParameter(typeParameter, c));
  }

  private static @Nullable PyTypeParameterType getTypeParameterTypeFromTypeParameter(@NotNull PsiElement element,
                                                                                     @NotNull Context context) {
    if (element instanceof PyTypeParameter typeParameter) {
      String name = typeParameter.getName();
      if (name == null) {
        return null;
      }

      ScopeOwner typeParameterOwner = ScopeUtil.getScopeOwner(typeParameter);
      PyQualifiedNameOwner scopeOwner = typeParameterOwner instanceof PyQualifiedNameOwner qualifiedNameOwner ? qualifiedNameOwner : null;

      String defaultExpressionText = typeParameter.getDefaultExpressionText();
      PyExpression defaultExpression = defaultExpressionText != null
                                       ? PyUtil.createExpressionFromFragment(defaultExpressionText, typeParameter)
                                       : null;
      Ref<PyType> defaultType = null;
      if (defaultExpression != null) {
        final PyExpression defaultExprWithoutParens = PyPsiUtils.flattenParens(defaultExpression);
        defaultType = defaultExprWithoutParens != null
                      ? getTypePreventingRecursion(defaultExprWithoutParens, context)
                      : Ref.create();
      }

      PyQualifiedNameOwner declarationElement = as(element, PyQualifiedNameOwner.class);

      switch (typeParameter.getKind()) {
        case TypeVar -> {
          List<@Nullable PyType> constraints = List.of();
          PyType boundType = null;
          String boundExpressionText = typeParameter.getBoundExpressionText();
          PyExpression boundExpression = boundExpressionText != null
                                         ? PyPsiUtils.flattenParens(PyUtil.createExpressionFromFragment(boundExpressionText, typeParameter))
                                         : null;
          if (boundExpression instanceof PyTupleExpression tupleExpression) {
            constraints = ContainerUtil.map(tupleExpression.getElements(), expr -> Ref.deref(getTypePreventingRecursion(expr, context)));
          }
          else if (boundExpression != null) {
            boundType = Ref.deref(getTypePreventingRecursion(boundExpression, context));
          }
          return new PyTypeVarTypeImpl(name, constraints, boundType, defaultType, PyTypeVarType.Variance.INFER_VARIANCE)
            .withScopeOwner(scopeOwner)
            .withDeclarationElement(declarationElement);
        }
        case ParamSpec -> {
          return new PyParamSpecType(name)
            .withScopeOwner(scopeOwner)
            .withDefaultType(
              Ref.deref(defaultType) instanceof PyCallableParameterVariadicType variadicType ? Ref.create(variadicType) : null)
            .withDeclarationElement(declarationElement);
        }
        case TypeVarTuple -> {
          return new PyTypeVarTupleTypeImpl(name)
            .withScopeOwner(scopeOwner)
            .withDefaultType(Ref.deref(defaultType) instanceof PyPositionalVariadicType variadicType ? Ref.create(variadicType) : null)
            .withDeclarationElement(declarationElement);
        }
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getTypePreventingRecursion(@NotNull PyExpression expression, @NotNull Context context) {
    return doPreventingRecursion(expression, false, () -> getType(expression, context));
  }

  // See https://peps.python.org/pep-0484/#scoping-rules-for-type-variables
  private static @Nullable PyQualifiedNameOwner getTypeParameterScope(@NotNull String name,
                                                                      @NotNull PyExpression typeHint,
                                                                      @NotNull Context context) {
    if (!context.myComputeTypeParameterScope) return null;

    PsiElement typeHintContext = getStubRetainedTypeHintContext(typeHint);
    List<PyQualifiedNameOwner> typeParamOwnerCandidates =
      StreamEx.iterate(typeHintContext, Objects::nonNull, owner -> PsiTreeUtil.getStubOrPsiParentOfType(owner, ScopeOwner.class))
        .filter(owner -> owner instanceof PyFunction || owner instanceof PyClass)
        .select(PyQualifiedNameOwner.class)
        .toList();

    PyQualifiedNameOwner closestOwner = ContainerUtil.getFirstItem(typeParamOwnerCandidates);
    if (closestOwner instanceof PyFunction) {
      Optional<PyTypeParameterType> typeParameterType = StreamEx.of(typeParamOwnerCandidates)
        .skip(1)
        .map(owner -> findSameTypeParameterInDefinition(owner, name, context))
        .nonNull()
        .findFirst();
      if (typeParameterType.isPresent()) {
        return typeParameterType.get().getScopeOwner();
      }
    }
    if (closestOwner != null) {
      boolean prevComputeTypeParameterScope = context.myComputeTypeParameterScope;
      context.myComputeTypeParameterScope = false;
      try {
        return findSameTypeParameterInDefinition(closestOwner, name, context) != null ? closestOwner : null;
      }
      finally {
        context.myComputeTypeParameterScope = prevComputeTypeParameterScope;
      }
    }

    // type aliases
    PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(typeHintContext, PyAssignmentStatement.class, false, PyStatement.class);
    if (assignment != null) {
      PyExpression assignedValue = PyPsiUtils.flattenParens(assignment.getAssignedValue());
      if (PsiTreeUtil.isAncestor(assignedValue, typeHintContext, false)) {
        if (PyPsiUtils.flattenParens(assignment.getLeftHandSideExpression()) instanceof PyTargetExpression target) {
          boolean isTypeParamDeclaration = assignedValue instanceof PyCallExpression callExpr &&
                                           getTypeParameterKindFromDeclaration(callExpr, context.getTypeContext()) != null;

          if (!isTypeParamDeclaration && PyTypingAliasStubType.looksLikeTypeHint(assignedValue)) {
            return target;
          }
        }
      }
    }

    return null;
  }

  private static @Nullable PyTypeParameterType findSameTypeParameterInDefinition(@NotNull PyQualifiedNameOwner owner,
                                                                                 @NotNull String name,
                                                                                 @NotNull Context context) {
    // At this moment, the definition of the TypeVar should be the type alias at the top of the stack.
    // While evaluating type hints of enclosing functions' parameters, resolving to the same TypeVar
    // definition shouldn't trigger the protection against recursive aliases, so we manually remove
    // it from the top for the time being.
    if (context.getTypeAliasStack().isEmpty()) {
      return null;
    }
    PyQualifiedNameOwner typeVarDeclaration = context.getTypeAliasStack().pop();
    assert typeVarDeclaration instanceof PyTargetExpression;
    try {
      final Iterable<PyTypeParameterType> typeParameters;
      if (owner instanceof PyClass cls) {
        typeParameters = collectTypeParameters(cls, context);
      }
      else if (owner instanceof PyFunction function) {
        typeParameters = collectTypeParameters(function, context.getTypeContext());
      }
      else {
        typeParameters = List.of();
      }
      return ContainerUtil.find(typeParameters, type -> name.equals(type.getName()));
    }
    finally {
      context.getTypeAliasStack().push(typeVarDeclaration);
    }
  }

  @ApiStatus.Internal
  public static @NotNull Iterable<PyTypeParameterType> collectTypeParameters(@NotNull PyFunction function,
                                                                             @NotNull TypeEvalContext context) {
    return StreamEx.of(function.getParameterList().getParameters())
      .select(PyNamedParameter.class)
      .map(parameter -> new PyTypingTypeProvider().getParameterType(parameter, function, context))
      .append(new PyTypingTypeProvider().getReturnType(function, context))
      .map(Ref::deref)
      .map(paramType -> PyTypeChecker.collectGenerics(paramType, context))
      .flatMap(generics -> StreamEx.<PyTypeParameterType>of(generics.getTypeVars())
        .append(generics.getParamSpecs())
        .append(generics.getTypeVarTuples())
      );
  }

  private static @NotNull PsiElement getStubRetainedTypeHintContext(@NotNull PsiElement typeHintExpression) {
    PsiFile containingFile = typeHintExpression.getContainingFile();
    // Values from PSI stubs and regular type comments
    PsiElement fragmentOwner = containingFile.getUserData(FRAGMENT_OWNER);
    if (fragmentOwner != null) {
      return fragmentOwner;
    }
    // Values from function type comments and string literals
    else if (containingFile instanceof PyFunctionTypeAnnotationFile || containingFile instanceof PyTypeHintFile) {
      return PyPsiUtils.getRealContext(typeHintExpression);
    }
    else {
      return typeHintExpression;
    }
  }

  public static @Nullable PyPositionalVariadicType getUnpackedType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
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

  private static @NotNull List<PyType> getIndexTypes(@NotNull PySubscriptionExpression expression, @NotNull Context context) {
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

  private static @Nullable PyCollectionType parameterizeClassDefaultAware(@NotNull PyClass pyClass,
                                                                          @NotNull List<PyType> actualTypeParams,
                                                                          @NotNull Context context) {
    PyCollectionType genericDefinitionType =
      doPreventingRecursion(pyClass, false, () -> PyTypeChecker.findGenericDefinitionType(pyClass, context.getTypeContext()));
    if (genericDefinitionType != null && ContainerUtil.exists(genericDefinitionType.getElementTypes(),
                                                              t -> t instanceof PyTypeParameterType typeParameterType &&
                                                                   typeParameterType.getDefaultType() != null)) {

      PyType parameterizedType = PyTypeChecker.parameterizeType(genericDefinitionType, actualTypeParams, context.myContext);
      if (parameterizedType instanceof PyCollectionType collectionType) {
        return collectionType;
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getTypeFromTypeAlias(@NotNull PyQualifiedNameOwner alias,
                                                            @NotNull PsiElement typeHint,
                                                            @NotNull PsiElement element,
                                                            @NotNull Context context) {
    if (element instanceof PyExpression assignedExpression) {
      if (alias instanceof PyTypeAliasStatement typeAliasStatement) {
        return getTypeFromTypeAliasStatement(typeAliasStatement, typeHint, assignedExpression, context);
      }

      @Nullable Ref<PyType> assignedTypeRef = getType(assignedExpression, context);
      if (assignedTypeRef != null) {
        @Nullable PyType assignedType = assignedTypeRef.get();
        if (assignedType == null) {
          return assignedTypeRef;
        }
        if (typeHint instanceof PySubscriptionExpression subscriptionExpr) {
          List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
          return Ref.create(PyTypeChecker.parameterizeType(assignedType, indexTypes, context.myContext));
        }
        if (typeHint instanceof PyReferenceExpression) {
          if (!(assignedType instanceof PyTypeParameterType)) {
            List<PyTypeParameterType> typeAliasTypeParams =
              PyTypeChecker.collectGenerics(assignedType, context.getTypeContext()).getAllTypeParameters();
            if (!typeAliasTypeParams.isEmpty()) {
              return Ref.create(PyTypeChecker.parameterizeType(assignedType, List.of(), context.myContext));
            }
            return Ref.create(assignedType);
          }
        }
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getTypeFromTypeAliasStatement(@NotNull PyTypeAliasStatement typeAliasStatement,
                                                                     @NotNull PsiElement typeHint,
                                                                     @NotNull PyExpression assignedExpression,
                                                                     @NotNull Context context) {
    @Nullable Ref<PyType> assignedTypeRef = getType(assignedExpression, context);
    if (assignedTypeRef != null) {
      PyType assignedType = assignedTypeRef.get();
      if (assignedType == null) {
        return assignedTypeRef;
      }
      List<PyType> indexTypes = typeHint instanceof PySubscriptionExpression subscriptionExpr
                                ? getIndexTypes(subscriptionExpr, context)
                                : Collections.emptyList();

      List<PyTypeParameterType> typeAliasTypeParams = collectTypeParametersFromTypeAliasStatement(typeAliasStatement, context);
      if (!typeAliasTypeParams.isEmpty()) {
        PyTypeChecker.GenericSubstitutions substitutions =
          PyTypeChecker.mapTypeParametersToSubstitutions(typeAliasTypeParams,
                                                         indexTypes,
                                                         Option.USE_DEFAULTS,
                                                         Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);

        return substitutions != null ? Ref.create(PyTypeChecker.substitute(assignedType, substitutions, context.myContext)) : null;
      }
      return assignedTypeRef;
    }
    return null;
  }

  private static @Nullable PyType getParameterizedType(@NotNull PsiElement element, @NotNull Context context) {
    if (element instanceof PySubscriptionExpression subscriptionExpr) {
      final PyExpression operand = subscriptionExpr.getOperand();
      final PyExpression indexExpr = subscriptionExpr.getIndexExpression();
      if (indexExpr != null) {
        final PyType operandType = Ref.deref(getType(operand, context));
        final List<PyType> indexTypes = getIndexTypes(subscriptionExpr, context);
        if (operandType != null) {
          if (operandType instanceof PyClassType classType) {
            if (!(operandType instanceof PyTupleType) && PyNames.TUPLE.equals(classType.getPyClass().getQualifiedName())) {
              if (indexExpr instanceof PyTupleExpression) {
                final PyExpression[] elements = ((PyTupleExpression)indexExpr).getElements();
                if (elements.length == 2 && elements[1] instanceof PyEllipsisLiteralExpression) {
                  return PyTupleType.createHomogeneous(element, indexTypes.get(0));
                }
              }
              return PyTupleType.create(element, indexTypes);
            }

            if (isGeneric(classType, context.myContext)) {
              PyCollectionType parameterizedType = parameterizeClassDefaultAware(classType.getPyClass(), indexTypes, context);
              if (parameterizedType != null) {
                return parameterizedType.toInstance();
              }
            }
            else {
              return null;
            }
          }
          return PyTypeChecker.parameterizeType(operandType, indexTypes, context.getTypeContext());
        }
      }
    }
    return null;
  }

  private static @Nullable PyType getCollection(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final String typingName = getQualifiedName(element);

    final String builtinName = BUILTIN_COLLECTION_CLASSES.get(typingName);
    if (builtinName != null) return PyTypeParser.getTypeByName(element, builtinName, context);

    final String collectionName = COLLECTIONS_CLASSES.get(typingName);
    if (collectionName != null) return PyTypeParser.getTypeByName(element, collectionName, context);

    return null;
  }

  private static @NotNull List<PsiElement> tryResolving(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    return ContainerUtil.map(tryResolvingWithAliases(expression, context), x -> x.getSecond());
  }

  private static @NotNull List<Pair<PyQualifiedNameOwner, PsiElement>> tryResolvingWithAliases(@NotNull PyExpression expression,
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
    if (expression instanceof PySubscriptionExpression subscriptionExpr) {
      // Possibly a parameterized type alias
      PyExpression operandExpression = subscriptionExpr.getOperand();
      List<Pair<PyQualifiedNameOwner, PsiElement>> results = tryResolvingWithAliases(operandExpression, context);
      for (Pair<PyQualifiedNameOwner, PsiElement> pair : results) {
        // If the parameterized type is a type alias
        if (pair.getFirst() != null && pair.getSecond() != null) {
          elements.add(Pair.create(pair.getFirst(), pair.getSecond()));
        }
      }
    }
    return !elements.isEmpty() ? elements : Collections.singletonList(Pair.create(null, expression));
  }

  private static @NotNull List<PsiElement> tryResolvingOnStubs(@NotNull PyReferenceExpression expression,
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
      return PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context);
    }
    return Collections.singletonList(expression);
  }

  public static @NotNull Collection<String> resolveToQualifiedNames(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final Set<String> names = new LinkedHashSet<>();
    for (PsiElement resolved : tryResolving(expression, context)) {
      final String name = getQualifiedName(resolved);
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }

  private static @Nullable String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PyQualifiedNameOwner qualifiedNameOwner) {
      return qualifiedNameOwner.getQualifiedName();
    }
    return null;
  }

  public static @Nullable PyType toAsyncIfNeeded(@NotNull PyFunction function, @Nullable PyType returnType) {
    if (function.isAsync() && function.isAsyncAllowed()) {
      if (!function.isGenerator()) {
        return wrapInCoroutineType(returnType, function);
      }
      var desc = GeneratorTypeDescriptor.create(returnType);
      if (desc != null) {
        return desc.withAsync(true).toPyType(function);
      }
    }

    return returnType;
  }

  /**
   * Bound narrowed types shouldn't leak out of its scope, since it is bound to a particular call site.
   */
  public static @Nullable PyType removeNarrowedTypeIfNeeded(@Nullable PyType type) {
    if (type instanceof PyNarrowedType pyNarrowedType && pyNarrowedType.isBound()) {
      return PyBuiltinCache.getInstance(pyNarrowedType.getOriginal()).getBoolType();
    }
    else {
      return type;
    }
  }

  private static @Nullable PyType wrapInCoroutineType(@Nullable PyType returnType, @NotNull PsiElement resolveAnchor) {
    final PyClass coroutine = PyPsiFacade.getInstance(resolveAnchor.getProject()).createClassByQName(COROUTINE, resolveAnchor);
    return coroutine != null ? new PyCollectionTypeImpl(coroutine, false, Arrays.asList(null, null, returnType)) : null;
  }

  public static @Nullable PyType wrapInGeneratorType(@Nullable PyType elementType,
                                                     @Nullable PyType sendType,
                                                     @Nullable PyType returnType,
                                                     @NotNull PsiElement anchor) {
    final PyClass generator = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(GENERATOR, anchor);
    return generator != null ? new PyCollectionTypeImpl(generator, false, Arrays.asList(elementType, sendType, returnType)) : null;
  }

  public record GeneratorTypeDescriptor(
    String className,
    PyType yieldType, // if YieldType is not specified, it is AnyType
    PyType sendType,  // if SendType is not specified, it is PyNoneType
    PyType returnType // if ReturnType is not specified, it is PyNoneType
  ) {

    private static final List<String> SYNC_TYPES = List.of(GENERATOR, "typing.Iterable", "typing.Iterator");
    private static final List<String> ASYNC_TYPES = List.of(ASYNC_GENERATOR, "typing.AsyncIterable", "typing.AsyncIterator");

    public static @Nullable GeneratorTypeDescriptor create(@Nullable PyType type) {
      final PyClassType classType = as(type, PyClassType.class);
      final PyCollectionType genericType = as(type, PyCollectionType.class);
      if (classType == null) return null;

      final String qName = classType.getClassQName();
      if (qName == null) return null;
      if (!SYNC_TYPES.contains(qName) && !ASYNC_TYPES.contains(qName)) return null;

      PyType yieldType = null;
      final var noneType = PyBuiltinCache.getInstance(classType.getPyClass()).getNoneType();
      PyType sendType = noneType;
      PyType returnType = noneType;

      if (genericType != null) {
        yieldType = ContainerUtil.getOrElse(genericType.getElementTypes(), 0, yieldType);
        if (GENERATOR.equals(qName) || ASYNC_GENERATOR.equals(qName)) {
          sendType = ContainerUtil.getOrElse(genericType.getElementTypes(), 1, sendType);
        }
        if (GENERATOR.equals(qName)) {
          returnType = ContainerUtil.getOrElse(genericType.getElementTypes(), 2, returnType);
        }
      }
      return new GeneratorTypeDescriptor(qName, yieldType, sendType, returnType);
    }

    public boolean isAsync() {
      return ASYNC_TYPES.contains(className);
    }

    public GeneratorTypeDescriptor withAsync(boolean async) {
      if (async) {
        var idx = SYNC_TYPES.indexOf(className);
        if (idx == -1) return this;
        return new GeneratorTypeDescriptor(ASYNC_TYPES.get(idx), yieldType, sendType, returnType);
      }
      else {
        var idx = ASYNC_TYPES.indexOf(className);
        if (idx == -1) return this;
        return new GeneratorTypeDescriptor(SYNC_TYPES.get(idx), yieldType, sendType, returnType);
      }
    }

    public @Nullable PyType toPyType(@NotNull PsiElement anchor) {
      final PyClass classType = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(className, anchor);
      final List<PyType> generics;
      if (GENERATOR.equals(className)) {
        generics = Arrays.asList(yieldType, sendType, returnType);
      }
      else if (ASYNC_GENERATOR.equals(className)) {
        generics = Arrays.asList(yieldType, sendType);
      }
      else {
        generics = Collections.singletonList(yieldType);
      }

      return classType != null ? new PyCollectionTypeImpl(classType, false, generics) : null;
    }
  }

  public static @Nullable Ref<PyType> unwrapCoroutineReturnType(@Nullable PyType coroutineType) {
    final PyCollectionType genericType = as(coroutineType, PyCollectionType.class);

    if (genericType != null) {
      var qName = genericType.getClassQName();

      if (AWAITABLE.equals(qName)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 0, null));
      }

      if (COROUTINE.equals(qName)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 2, null));
      }
    }

    return null;
  }

  public static @Nullable Ref<PyType> coroutineOrGeneratorElementType(@Nullable PyType coroutineOrGeneratorType) {
    final PyCollectionType genericType = as(coroutineOrGeneratorType, PyCollectionType.class);
    final PyClassType classType = as(coroutineOrGeneratorType, PyClassType.class);

    if (genericType != null && classType != null) {
      var qName = classType.getClassQName();

      if (AWAITABLE.equals(qName)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 0, null));
      }

      if (ArrayUtil.contains(qName, COROUTINE, GENERATOR)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 2, null));
      }
    }

    return null;
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

  @ApiStatus.Internal
  public static final class Context {
    private final @NotNull TypeEvalContext myContext;
    private final @NotNull Stack<PyQualifiedNameOwner> myTypeAliasStack = new Stack<>();
    private boolean myComputeTypeParameterScope = true;

    private Context(@NotNull TypeEvalContext context) {
      myContext = context;
    }

    public @NotNull TypeEvalContext getTypeContext() {
      return myContext;
    }

    public @NotNull Stack<PyQualifiedNameOwner> getTypeAliasStack() {
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
}
