// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyTypeParameterMapping.Option;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.jetbrains.python.PyNames.FUNCTION;
import static com.jetbrains.python.psi.PyUtil.*;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;

public final class PyTypeChecker {
  private PyTypeChecker() {
  }

  /**
   * See {@link PyTypeChecker#match(PyType, PyType, TypeEvalContext, Map)} for description.
   */
  public static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    @NotNull GenericSubstitutions substitutions = new GenericSubstitutions();
    return match(expected, actual, new MatchContext(context, substitutions, false)).orElse(true);
  }

  /**
   * Checks whether a type {@code actual} can be placed where {@code expected} is expected.
   *
   * For example {@code int} matches {@code object}, while {@code str} doesn't match {@code int}.
   * Work for builtin types, classes, tuples etc.
   *
   * Whether it's unknown if {@code actual} match {@code expected} the method returns {@code true}.
   *
   * @implNote This behavior may be changed in future by replacing {@code boolean} with {@code Optional<Boolean>} and updating the clients.
   *
   * @param expected expected type
   * @param actual type to be matched against expected
   * @param context type evaluation context
   * @param substitutions map of substitutions for {@code expected} type
   * @return {@code false} if {@code expected} and {@code actual} don't match, true otherwise
   */
  public static boolean match(@Nullable PyType expected,
                              @Nullable PyType actual,
                              @NotNull TypeEvalContext context,
                              @NotNull Map<? extends PyTypeParameterType, PyType> typeVars) {
    var substitutions = new GenericSubstitutions(typeVars);
    return match(expected, actual, new MatchContext(context, substitutions, false)).orElse(true);
  }

  public static boolean match(@Nullable PyType expected,
                              @Nullable PyType actual,
                              @NotNull TypeEvalContext context,
                              @NotNull GenericSubstitutions substitutions) {
    return match(expected, actual, new MatchContext(context, substitutions, false))
      .orElse(true);
  }

  @NotNull
  private static Optional<Boolean> match(@Nullable PyType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    final Optional<Boolean> result = RecursionManager.doPreventingRecursion(
      Pair.create(expected, actual),
      false,
      () -> matchImpl(expected, actual, context)
    );

    return result == null ? Optional.of(true) : result;
  }

  /**
   * Perform type matching.
   *
   * Implementation details:
   * <ul>
   *  <li>The method mutates {@code context.substitutions} map adding new entries into it
   *  <li>The order of match subroutine calls is important
   *  <li>The method may recursively call itself
   * </ul>
   */
  @NotNull
  private static Optional<Boolean> matchImpl(@Nullable PyType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    if (Objects.equals(expected, actual)) {
      return Optional.of(true);
    }

    for (PyTypeCheckerExtension extension : PyTypeCheckerExtension.EP_NAME.getExtensionList()) {
      final Optional<Boolean> result = extension.match(expected, actual, context.context, context.mySubstitutions);
      if (result.isPresent()) {
        return result;
      }
    }

    if (expected instanceof PyClassType) {
      Optional<Boolean> match = matchObject((PyClassType)expected, actual);
      if (match.isPresent()) {
        return match;
      }
    }

    if (actual instanceof PyTypeVarTupleType typeVarTupleType && context.reversedSubstitutions) {
      return Optional.of(match(typeVarTupleType, expected, context));
    }

    if (expected instanceof PyVariadicType variadic) {
      return Optional.of(match(variadic, actual, context));
    }

    if (actual instanceof PyTypeVarType typeVarType && context.reversedSubstitutions) {
      return Optional.of(match(typeVarType, expected, context));
    }

    if (expected instanceof PyTypeVarType typeVarType) {
      return Optional.of(match(typeVarType, actual, context));
    }

    if (expected instanceof PySelfType) {
      return match(context.mySubstitutions.qualifierType, actual, context);
    }

    if (actual instanceof PySelfType && context.reversedSubstitutions) {
      return match(context.mySubstitutions.qualifierType, expected, context);
    }

    if (expected instanceof PyParamSpecType) {
      return Optional.of(match((PyParamSpecType)expected, actual, context));
    }

    if (expected == null || actual == null || isUnknown(actual, context.context)) {
      return Optional.of(true);
    }

    if (actual instanceof PyUnionType) {
      return Optional.of(match(expected, (PyUnionType)actual, context));
    }

    if (expected instanceof PyUnionType) {
      return Optional.of(match((PyUnionType)expected, actual, context));
    }

    if (expected instanceof PyClassType && actual instanceof PyClassType) {
      Optional<Boolean> match = match((PyClassType)expected, (PyClassType)actual, context);
      if (match.isPresent()) {
        return match;
      }
    }

    if (actual instanceof PyStructuralType && ((PyStructuralType)actual).isInferredFromUsages()) {
      return Optional.of(true);
    }

    if (expected instanceof PyStructuralType) {
      return Optional.of(match((PyStructuralType)expected, actual, context.context));
    }

    if (actual instanceof PyStructuralType && expected instanceof PyClassType) {
      final Set<String> expectedAttributes = ((PyClassType)expected).getMemberNames(true, context.context);
      return Optional.of(expectedAttributes.containsAll(((PyStructuralType)actual).getAttributeNames()));
    }

    if (actual instanceof PyCallableType actualCallable && expected instanceof PyCallableType expectedCallable) {
      final Optional<Boolean> match = match(expectedCallable, actualCallable, context);
      if (match.isPresent()) {
        return match;
      }
    }

    // remove after making PyNoneType inheriting PyClassType
    if (expected instanceof PyNoneType) {
      return Optional.of(actual instanceof PyNoneType);
    }

    if (expected instanceof PyModuleType) {
      return Optional.of(actual instanceof PyModuleType && ((PyModuleType)expected).getModule() == ((PyModuleType)actual).getModule());
    }

    if (expected instanceof PyClassType && actual instanceof PyModuleType) {
      return match(expected, ((PyModuleType)actual).getModuleClassType(), context);
    }

    return Optional.of(matchNumericTypes(expected, actual));
  }

  /**
   * Check whether {@code expected} is Python *object* or *type*.
   *
   * {@see PyTypeChecker#match(PyType, PyType, TypeEvalContext, Map)}
   */
  @NotNull
  private static Optional<Boolean> matchObject(@NotNull PyClassType expected, @Nullable PyType actual) {
    if (ArrayUtil.contains(expected.getName(), PyNames.OBJECT, PyNames.TYPE)) {
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(expected.getPyClass());
      if (expected.equals(builtinCache.getObjectType())) {
        return Optional.of(true);
      }
      if (expected.equals(builtinCache.getTypeType()) &&
          actual instanceof PyInstantiableType && ((PyInstantiableType<?>)actual).isDefinition()) {
        return Optional.of(true);
      }
    }
    return Optional.empty();
  }

  /**
   * Match {@code actual} versus {@link PyTypeVarType} expected.
   *
   * The method mutates {@code context.substitutions} map adding new entries into it
   */
  private static boolean match(@NotNull PyTypeVarType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    if (expected.isDefinition() && actual instanceof PyInstantiableType && !((PyInstantiableType<?>)actual).isDefinition()) {
      return false;
    }

    final PyType substitution = context.mySubstitutions.typeVars.get(expected);
    PyType bound = expected.getBound();
    // Promote int in Type[TypeVar('T', int)] to Type[int] before checking that bounds match
    if (expected.isDefinition()) {
      final Function<PyType, PyType> toDefinition = t -> t instanceof PyInstantiableType ? ((PyInstantiableType<?>)t).toClass() : t;
      bound = PyUnionType.union(PyTypeUtil.toStream(bound).map(toDefinition).toList());
    }

    // Remove value-specific components from the actual type to make it safe to propagate
    PyType safeActual = bound instanceof PyLiteralStringType ? actual : replaceLiteralStringWithStr(actual);

    Optional<Boolean> match = match(bound, safeActual, context);
    if (match.isPresent() && !match.get()) {
      return false;
    }

    if (substitution != null) {
      if (expected.equals(safeActual) || substitution.equals(expected)) {
        return true;
      }

      Optional<Boolean> recursiveMatch = RecursionManager.doPreventingRecursion(
        expected, false, context.reversedSubstitutions
                         ? () -> match(safeActual, substitution, context)
                         : () -> match(substitution, safeActual, context)
      );
      return recursiveMatch != null ? recursiveMatch.orElse(false) : false;
    }

    if (safeActual != null) {
      context.mySubstitutions.typeVars.put(expected, safeActual);
    }
    else if (bound != null) {
      context.mySubstitutions.typeVars.put(expected, PyUnionType.createWeakType(bound));
    }

    return true;
  }

  private static boolean match(@NotNull PyVariadicType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    if (actual == null) {
      return true;
    }
    if (!(actual instanceof PyVariadicType actualVariadic)) {
      return false;
    }
    if (expected instanceof PyUnpackedTupleType expectedUnpackedTupleType) {
      // The actual type is just a TypeVarTuple
      if (!(actualVariadic instanceof PyUnpackedTupleType actualUnpackedTupleType)) {
        return false;
      }
      if (expectedUnpackedTupleType.isUnbound()) {
        PyType repeatedExpectedType = expectedUnpackedTupleType.getElementTypes().get(0);
        if (actualUnpackedTupleType.isUnbound()) {
          return match(repeatedExpectedType, actualUnpackedTupleType.getElementTypes().get(0), context).orElse(false);
        }
        else {
          return ContainerUtil.all(actualUnpackedTupleType.getElementTypes(),
                                   singleActualType -> match(repeatedExpectedType, singleActualType, context).orElse(false));
        }
      }
      else {
        if (actualUnpackedTupleType.isUnbound()) {
          PyType repeatedActualType = actualUnpackedTupleType.getElementTypes().get(0);
          return ContainerUtil.all(expectedUnpackedTupleType.getElementTypes(),
                                   singleExpectedType -> match(singleExpectedType, repeatedActualType, context).orElse(false));
        }
        else {
          return matchTypeParameters(expectedUnpackedTupleType.getElementTypes(), actualUnpackedTupleType.getElementTypes(), context);
        }
      }
    }
    // The expected type is just a TypeVarTuple
    else {
      PyVariadicType substitution = context.mySubstitutions.typeVarTuples.get(expected);
      if (substitution != null && !substitution.equals(PyUnpackedTupleTypeImpl.UNSPECIFIED)) {
        if (expected.equals(actual) || substitution.equals(expected)) {
          return true;
        }
        return context.reversedSubstitutions ? match(actualVariadic, substitution, context) : match(substitution, actualVariadic, context);
      }
      context.mySubstitutions.typeVarTuples.put((PyTypeVarTupleType)expected, actualVariadic);
    }
    return true;
  }

  private static @Nullable PyType replaceLiteralStringWithStr(@Nullable PyType actual) {
    // TODO replace with PyTypeVisitor API once it's ready
    if (actual instanceof PyLiteralStringType literalStringType) {
      return new PyClassTypeImpl(literalStringType.getPyClass(), false);
    }
    if (actual instanceof PyUnionType unionType) {
      return unionType.map(PyTypeChecker::replaceLiteralStringWithStr);
    }
    if (actual instanceof PyNamedTupleType) {
      return actual;
    }
    if (actual instanceof PyTupleType tupleType) {
      return new PyTupleType(tupleType.getPyClass(),
                             ContainerUtil.map(tupleType.getElementTypes(), PyTypeChecker::replaceLiteralStringWithStr),
                             tupleType.isHomogeneous(), tupleType.isDefinition());
    }
    if (actual instanceof PyCollectionType generic) {
      return new PyCollectionTypeImpl(generic.getPyClass(), generic.isDefinition(),
                                      ContainerUtil.map(generic.getElementTypes(), PyTypeChecker::replaceLiteralStringWithStr));
    }
    return actual;
  }

  private static boolean match(@NotNull PyParamSpecType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    if (actual == null) return true;
    if (!(actual instanceof PyParamSpecType callableActual)) return false;
    final var parameters = callableActual.getParameters();
    if (parameters == null) return false;
    context.mySubstitutions.paramSpecs.put(expected, expected.withParameters(parameters, context.context));
    return true;
  }

  private static boolean match(@NotNull PyType expected, @NotNull PyUnionType actual, @NotNull MatchContext context) {
    if (expected instanceof PyTupleType expectedTupleType) {
      // XXX A type-widening hack for cases like PyTypeTest.testDictFromTuple
      Optional<Boolean> match = match(expectedTupleType, actual, context);
      if (match.isPresent()) {
        return match.get();
      }
    }

    // checking strictly separately until PY-24834 gets implemented
    if (ContainerUtil.exists(actual.getMembers(), x -> x instanceof PyLiteralStringType || x instanceof PyLiteralType)) {
      return ContainerUtil.and(actual.getMembers(), type -> match(expected, type, context).orElse(false));
    }

    return ContainerUtil.or(actual.getMembers(), type -> match(expected, type, context).orElse(false));
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyTupleType expected, @NotNull PyUnionType actual, @NotNull MatchContext context) {
    final int elementCount = expected.getElementCount();

    if (!expected.isHomogeneous()) {
      PyTupleType widenedActual = widenUnionOfTuplesToTupleOfUnions(actual, elementCount);
      if (widenedActual != null) {
        return match(expected, widenedActual, context);
      }
    }

    return Optional.empty();
  }

  private static boolean match(@NotNull PyUnionType expected, @NotNull PyType actual, @NotNull MatchContext context) {
    if (expected.getMembers().contains(actual)) {
      return true;
    }
    return ContainerUtil.or(expected.getMembers(), type -> match(type, actual, context).orElse(true));
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyClassType expected, @NotNull PyClassType actual, @NotNull MatchContext matchContext) {
    if (expected.equals(actual)) {
      return Optional.of(true);
    }

    final TypeEvalContext context = matchContext.context;

    if (expected.isDefinition() ^ actual.isDefinition() && !PyProtocolsKt.isProtocol(expected, context)) {
      if (!expected.isDefinition() && actual.isDefinition()) {
        final PyClassLikeType metaClass = actual.getMetaClassType(context, true);
        return Optional.of(metaClass != null && match((PyType)expected, metaClass.toInstance(), matchContext).orElse(true));
      }
      return Optional.of(false);
    }

    if (expected instanceof PyTupleType && actual instanceof PyTupleType) {
      return match((PyTupleType)expected, (PyTupleType)actual, matchContext);
    }

    if (expected instanceof PyLiteralType) {
      return Optional.of(actual instanceof PyLiteralType && PyLiteralType.Companion.match((PyLiteralType)expected, (PyLiteralType)actual));
    }

    if (actual instanceof PyTypedDictType) {
      final PyTypedDictType.TypeCheckingResult matchResult = PyTypedDictType.Companion.checkTypes(expected, (PyTypedDictType)actual, context, null);
      if (matchResult != null) return Optional.of(matchResult.getMatch());
    }

    if (expected instanceof PyLiteralStringType) {
      return Optional.of(PyLiteralStringType.Companion.match((PyLiteralStringType)expected, actual));
    }

    final PyClass superClass = expected.getPyClass();
    final PyClass subClass = actual.getPyClass();

    if (!subClass.isSubclass(superClass, context) && PyProtocolsKt.isProtocol(expected, context)) {
      return Optional.of(matchProtocols(expected, actual, matchContext));
    }

    if (expected instanceof PyCollectionType) {
      return Optional.of(match((PyCollectionType)expected, actual, matchContext));
    }

    if (matchClasses(superClass, subClass, context)) {
      if (expected instanceof PyTypingNewType && !expected.equals(actual) && superClass.equals(subClass)) {
        return Optional.of(actual.getAncestorTypes(context).contains(expected));
      }
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private static boolean matchProtocols(@NotNull PyClassType expected, @NotNull PyClassType actual, @NotNull MatchContext matchContext) {
    GenericSubstitutions substitutions = collectTypeSubstitutions(actual, matchContext.context);

    MatchContext protocolContext = new MatchContext(matchContext.context, new GenericSubstitutions(), matchContext.reversedSubstitutions);
    for (kotlin.Pair<PyTypedElement, List<RatedResolveResult>> pair : PyProtocolsKt.inspectProtocolSubclass(expected, actual, matchContext.context)) {
      final List<RatedResolveResult> subclassElements = pair.getSecond();
      if (ContainerUtil.isEmpty(subclassElements)) {
        return false;
      }

      final PyType protocolElementType = dropSelfIfNeeded(expected, matchContext.context.getType(pair.getFirst()), matchContext.context);
      final boolean elementResult = StreamEx
        .of(subclassElements)
        .map(ResolveResult::getElement)
        .select(PyTypedElement.class)
        .map(matchContext.context::getType)
        .map(type -> dropSelfIfNeeded(actual, type, matchContext.context))
        .map(type -> substitute(type, substitutions, matchContext.context))
        .anyMatch(
          subclassElementType -> {
            boolean matched = match(protocolElementType, subclassElementType, protocolContext).orElse(true);
            if (!matched) return false;
            if (!(protocolElementType instanceof PyFunctionType) || !(subclassElementType instanceof PyFunctionType)) return matched;
            var protocolReturnType = ((PyFunctionType)protocolElementType).getReturnType(protocolContext.context);
            if (protocolReturnType instanceof PySelfType) {
              var subclassReturnType = ((PyFunctionType)subclassElementType).getReturnType(protocolContext.context);
              if (subclassReturnType instanceof PySelfType) return true;
              return match(actual, subclassReturnType, matchContext).orElse(true);
            }
            return matched;
          }
        );

      if (!elementResult) {
        return false;
      }
    }


    if (expected instanceof PyCollectionType) {
      PyCollectionType genericSuperClass = findGenericDefinitionType(expected.getPyClass(), matchContext.context);
      if (genericSuperClass != null) {
        PyCollectionType concreteSuperClass = (PyCollectionType)substitute(genericSuperClass, protocolContext.mySubstitutions, protocolContext.context);
        assert concreteSuperClass != null;
        return matchGenericClassesParameterWise((PyCollectionType)expected, concreteSuperClass, matchContext);
      }
    }

    return true;
  }

  private static @Nullable PyType dropSelfIfNeeded(@NotNull PyClassType classType,
                                                   @Nullable PyType elementType,
                                                   @NotNull TypeEvalContext context) {
    if (elementType instanceof PyFunctionType functionType) {
      if (PyUtil.isInitOrNewMethod(functionType.getCallable()) || !classType.isDefinition()) {
        return functionType.dropSelf(context);
      }
    }
    return elementType;
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyTupleType expected, @NotNull PyTupleType actual, @NotNull MatchContext context) {
    // TODO Delegate to UnpackedTupleType here, once it's introduced
    if (!expected.isHomogeneous() && !actual.isHomogeneous()) {
      return Optional.of(matchTypeParameters(expected.getElementTypes(), actual.getElementTypes(), context));
    }

    if (expected.isHomogeneous() && !actual.isHomogeneous()) {
      final PyType expectedElementType = expected.getIteratedItemType();
      for (int i = 0; i < actual.getElementCount(); i++) {
        if (!match(expectedElementType, actual.getElementType(i), context).orElse(true)) {
          return Optional.of(false);
        }
      }
      return Optional.of(true);
    }

    if (!expected.isHomogeneous() && actual.isHomogeneous()) {
      return Optional.of(false);
    }

    return match(expected.getIteratedItemType(), actual.getIteratedItemType(), context);
  }

  private static boolean match(@NotNull PyCollectionType expected, @NotNull PyClassType actual, @NotNull MatchContext context) {
    if (actual instanceof PyTupleType) {
      return match(expected, (PyTupleType)actual, context);
    }

    final PyClass superClass = expected.getPyClass();
    final PyClass subClass = actual.getPyClass();

    return matchClasses(superClass, subClass, context.context) && matchGenerics(expected, actual, context);
  }

  private static boolean match(@NotNull PyCollectionType expected, @NotNull PyTupleType actual, @NotNull MatchContext context) {
    if (!matchClasses(expected.getPyClass(), actual.getPyClass(), context.context)) {
      return false;
    }

    final PyType superElementType = expected.getIteratedItemType();
    final PyType subElementType = actual.getIteratedItemType();

    return match(superElementType, subElementType, context).orElse(true);
  }

  private static boolean match(@NotNull PyStructuralType expected, @NotNull PyType actual, @NotNull TypeEvalContext context) {
    if (actual instanceof PyStructuralType) {
      return match(expected, (PyStructuralType)actual);
    }
    if (actual instanceof PyClassType) {
      return match(expected, (PyClassType)actual, context);
    }
    if (actual instanceof PyModuleType) {
      final PyFile module = ((PyModuleType)actual).getModule();
      if (module.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON37) && definesGetAttr(module, context)) {
        return true;
      }
    }

    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    return !ContainerUtil.exists(expected.getAttributeNames(), attribute -> ContainerUtil
      .isEmpty(actual.resolveMember(attribute, null, AccessDirection.READ, resolveContext)));
  }

  private static boolean match(@NotNull PyStructuralType expected, @NotNull PyStructuralType actual) {
    if (expected.isInferredFromUsages()) {
      return true;
    }
    return expected.getAttributeNames().containsAll(actual.getAttributeNames());
  }

  private static boolean match(@NotNull PyStructuralType expected, @NotNull PyClassType actual, @NotNull TypeEvalContext context) {
    if (overridesGetAttr(actual.getPyClass(), context)) {
      return true;
    }
    final Set<String> actualAttributes = actual.getMemberNames(true, context);
    return actualAttributes.containsAll(expected.getAttributeNames());
  }

  private static boolean matchCallableParameters(@NotNull List<PyCallableParameter> expectedParameters,
                                                 @NotNull List<PyCallableParameter> actualParameters,
                                                 @NotNull MatchContext matchContext) {
    final TypeEvalContext context = matchContext.context;

    if (expectedParameters.size() == 1) {
      final var firstExpectedParam = expectedParameters.get(0);
      final var expectedParamType = firstExpectedParam.getType(context);
      if (expectedParamType instanceof final PyParamSpecType expectedParamSpecType) {
        matchContext.mySubstitutions.paramSpecs.put(expectedParamSpecType, expectedParamSpecType.withParameters(actualParameters, context));
        return true;
      }
      else if (expectedParamType instanceof final PyConcatenateType expectedConcatenateType) {
        if (actualParameters.isEmpty()) {
          return true;
        }
        final var actualParamType = actualParameters.get(0).getType(context);
        final var expectedFirstTypes = expectedConcatenateType.getFirstTypes();

        if (actualParamType instanceof final PyConcatenateType actualConcatenateType) {
          final var actualFirstType = actualConcatenateType.getFirstTypes();
          if (!match(expectedFirstTypes, actualFirstType, matchContext)) {
            return false;
          }
        }
        else {
          final var actualParamRightBound = Math.min(expectedFirstTypes.size(), actualParameters.size());
          final var actualFirstParamTypes = ContainerUtil
            .map(actualParameters.subList(0, actualParamRightBound), it -> it.getType(context));

          if (!match(expectedFirstTypes, actualFirstParamTypes, matchContext)) {
            return false;
          }

          if (actualParamRightBound < actualParameters.size()) {
            final var expectedParamSpecType = expectedConcatenateType.getParamSpec();
            final var restActualParameters = actualParameters.subList(actualParamRightBound, actualParameters.size());
            final var parametersSubst = expectedParamSpecType.withParameters(restActualParameters, context);
            matchContext.mySubstitutions.paramSpecs.put(expectedParamSpecType, parametersSubst);
            return true;
          }
        }

        return true;
      }
    }

    int startIndex = 0;
    if (!expectedParameters.isEmpty() && !actualParameters.isEmpty()) {
      var firstExpectedParam = expectedParameters.get(0);
      var firstActualParam = actualParameters.get(0);
      if (firstExpectedParam.isSelf() && firstActualParam.isSelf()) {
        if (!match(firstExpectedParam.getType(context), firstActualParam.getType(context), matchContext).orElse(true)) {
          return false;
        }
        startIndex = 1;
      }
    }

    // TODO Implement proper compatibility check for callable signatures, including positional- and keyword-only arguments, defaults, etc.
    boolean shouldAcceptUnlimitedPositionalArgs = ContainerUtil.exists(expectedParameters, PyCallableParameter::isPositionalContainer);
    boolean canAcceptUnlimitedPositionalArgs = ContainerUtil.exists(actualParameters, PyCallableParameter::isPositionalContainer);
    if (shouldAcceptUnlimitedPositionalArgs && !canAcceptUnlimitedPositionalArgs) return false;

    boolean shouldAcceptArbitraryKeywordArgs = ContainerUtil.exists(expectedParameters, PyCallableParameter::isKeywordContainer);
    boolean canAcceptArbitraryKeywordArgs = ContainerUtil.exists(actualParameters, PyCallableParameter::isKeywordContainer);
    if (shouldAcceptArbitraryKeywordArgs && !canAcceptArbitraryKeywordArgs) return false;

    List<PyType> expectedElementTypes = StreamEx.of(expectedParameters)
      .filter(cp -> !(cp.getParameter() instanceof PySlashParameter || cp.getParameter() instanceof PySingleStarParameter))
      .map(cp -> {
        PyType argType = cp.getArgumentType(context);
        if (cp.isPositionalContainer() && !(argType instanceof PyVariadicType)) {
          return PyUnpackedTupleTypeImpl.createUnbound(argType);
        }
        return argType;
      })
      .toList();
    PyTypeParameterMapping mapping = PyTypeParameterMapping.mapWithParameterList(ContainerUtil.subList(expectedElementTypes, startIndex),
                                                                                 ContainerUtil.subList(actualParameters, startIndex),
                                                                                 context);
    if (mapping == null) {
      return false;
    }
    // actual callable type could accept more general parameter type
    for (Couple<PyType> pair : mapping.getMappedTypes()) {
      Optional<Boolean> matched = matchContext.reverseSubstitutions().reversedSubstitutions
                                  ? match(pair.getSecond(), pair.getFirst(), matchContext.reverseSubstitutions())
                                  : match(pair.getFirst(), pair.getSecond(), matchContext.reverseSubstitutions());
      if (!matched.orElse(true)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyCallableType expected,
                                         @NotNull PyCallableType actual,
                                         @NotNull MatchContext matchContext) {
    if (actual instanceof PyFunctionType && expected instanceof PyClassType && FUNCTION.equals(expected.getName())
        && expected.equals(PyBuiltinCache.getInstance(actual.getCallable()).getObjectType(FUNCTION))) {
      return Optional.of(true);
    }

    final TypeEvalContext context = matchContext.context;

    if (expected instanceof PyClassLikeType && !isCallableProtocol((PyClassLikeType)expected, context)) {
      return PyTypingTypeProvider.CALLABLE.equals(((PyClassLikeType)expected).getClassQName())
             ? Optional.of(actual.isCallable())
             : Optional.empty();
    }

    if (expected.isCallable() && actual.isCallable()) {
      final List<PyCallableParameter> expectedParameters = expected.getParameters(context);
      final List<PyCallableParameter> actualParameters = actual.getParameters(context);
      if (expectedParameters != null && actualParameters != null) {
        if (!matchCallableParameters(expectedParameters, actualParameters, matchContext)) {
          return Optional.of(false);
        }
      }
      if (!match(expected.getReturnType(context), getActualReturnType(actual, context), matchContext).orElse(true)) {
        return Optional.of(false);
      }
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private static boolean match(@NotNull List<PyType> expected, @NotNull List<PyType> actual, @NotNull MatchContext matchContext) {
    final var size = Math.min(expected.size(), actual.size());
    for (int i = 0; i < size; ++i) {
      if (!match(expected.get(i), actual.get(i), matchContext).orElse(true)) return false;
    }
    return true;
  }

  private static boolean isCallableProtocol(@NotNull PyClassLikeType expected, @NotNull TypeEvalContext context) {
    return PyProtocolsKt.isProtocol(expected, context) && expected.getMemberNames(false, context).contains(PyNames.CALL);
  }

  private static @Nullable PyType getActualReturnType(@NotNull PyCallableType actual, @NotNull TypeEvalContext context) {
    PyCallable callable = actual.getCallable();
    if (callable instanceof PyFunction) {
      return getReturnTypeToAnalyzeAsCallType((PyFunction)callable, context);
    }
    return actual.getReturnType(context);
  }

  private static @Nullable PyTupleType widenUnionOfTuplesToTupleOfUnions(@NotNull PyUnionType unionType, int elementCount) {
    boolean consistsOfSameSizeTuples = ContainerUtil.all(unionType.getMembers(), member -> member instanceof PyTupleType tupleType &&
                                                                                           !tupleType.isHomogeneous() &&
                                                                                           elementCount == tupleType.getElementCount());
    if (!consistsOfSameSizeTuples) {
      return null;
    }
    List<PyType> newTupleElements = IntStreamEx.range(elementCount)
      .mapToObj(index -> PyTypeUtil.toStream(unionType)
        .select(PyTupleType.class)
        .map(tupleType -> tupleType.getElementType(index))
        .collect(PyTypeUtil.toUnion()))
      .toList();
    PyClass tupleClass = ((PyTupleType)ContainerUtil.getFirstItem(unionType.getMembers())).getPyClass();
    return new PyTupleType(tupleClass, newTupleElements, false);
  }

  private static boolean matchGenerics(@NotNull PyCollectionType expected, @NotNull PyClassType actual, @NotNull MatchContext context) {
    if (actual instanceof PyCollectionType && expected.getPyClass().equals(actual.getPyClass())) {
      return matchGenericClassesParameterWise(expected, (PyCollectionType)actual, context);
    }

    PyCollectionType expectedGenericType = findGenericDefinitionType(expected.getPyClass(), context.context);
    if (expectedGenericType != null) {
      GenericSubstitutions actualSubstitutions = collectTypeSubstitutions(actual, context.context);
      PyCollectionType concreteExpected = (PyCollectionType)substitute(expectedGenericType, actualSubstitutions, context.context);
      assert concreteExpected != null;
      return matchGenericClassesParameterWise(expected, concreteExpected, context);
    }
    return true;
  }

  // TODO Make it a part of PyClassType interface
  private static @NotNull GenericSubstitutions collectTypeSubstitutions(@NotNull PyClassType classType, @NotNull TypeEvalContext context) {
    GenericSubstitutions result = new GenericSubstitutions();
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      Map<PyType, PyType> substitutionsFromClassDefinition = provider.getGenericSubstitutions(classType.getPyClass(), context);
      for (Map.Entry<PyType, PyType> entry : substitutionsFromClassDefinition.entrySet()) {
        if (entry.getKey() instanceof PyTypeVarType typeVarType) {
          result.typeVars.put(typeVarType, entry.getValue());
        }
        else if (entry.getKey() instanceof PyTypeVarTupleType typeVarTuple) {
          assert entry.getValue() instanceof PyVariadicType;
          result.typeVarTuples.put(typeVarTuple, (PyVariadicType)entry.getValue());
        }
        // TODO Handle ParamSpecs here
      }
      if (!classType.isDefinition()) {
        PyCollectionType genericDefinitionType = as(provider.getGenericType(classType.getPyClass(), context), PyCollectionType.class);
        // TODO Re-use PyTypeParameterMapping, at the moment C[*Ts] <- C leads to *Ts being mapped to *tuple[], which breaks inference later on
        if (genericDefinitionType != null) {
          List<PyType> definitionTypeParameters = genericDefinitionType.getElementTypes();
          if (!(classType instanceof PyCollectionType genericType)) {
            for (PyType typeParameter : definitionTypeParameters) {
              if (typeParameter instanceof PyTypeVarTupleType typeVarTupleType) {
                result.typeVarTuples.put(typeVarTupleType, null);
              }
              else if (typeParameter instanceof PyParamSpecType paramSpecType) {
                result.paramSpecs.put(paramSpecType, null);
              }
              else if (typeParameter instanceof PyTypeVarType typeVarType) {
                result.typeVars.put(typeVarType, null);
              }
            }
          }
          else {
            PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(definitionTypeParameters, genericType.getElementTypes(),
                                                                               Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
            if (mapping != null) {
              for (Couple<PyType> pair : mapping.getMappedTypes()) {
                PyType typeParameter = pair.getFirst();
                PyType typeArgument = pair.getSecond();
                if (typeParameter instanceof PyTypeVarType typeVarType) {
                  result.typeVars.put(typeVarType, typeArgument);
                }
                else if (typeParameter instanceof PyTypeVarTupleType typeVarTuple) {
                  assert typeArgument instanceof PyVariadicType || typeArgument == null;
                  result.typeVarTuples.put(typeVarTuple, (PyVariadicType)typeArgument);
                }
                else if (typeParameter instanceof PyParamSpecType) {
                  result.paramSpecs.put((PyParamSpecType)typeParameter, as(typeArgument, PyParamSpecType.class));
                }
              }
            }
          }
        }
      }
      if (!result.typeVars.isEmpty() || !result.typeVarTuples.isEmpty() || !result.paramSpecs.isEmpty()) {
        return result;
      }
    }
    return result;
  }

  @ApiStatus.Internal
  public static @Nullable PyCollectionType findGenericDefinitionType(@NotNull PyClass pyClass, @NotNull TypeEvalContext context) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      PyType definitionType = provider.getGenericType(pyClass, context);
      if (definitionType instanceof PyCollectionType) {
        return (PyCollectionType)definitionType;
      }
    }
    return null;
  }

  private static boolean matchGenericClassesParameterWise(@NotNull PyCollectionType expected,
                                                          @NotNull PyCollectionType actual,
                                                          @NotNull MatchContext context) {
    if (expected.equals(actual)) {
      return true;
    }
    if (!expected.getPyClass().equals(actual.getPyClass())) {
      return false;
    }
    List<PyType> expectedElementTypes = expected.getElementTypes();
    List<PyType> actualElementTypes = actual.getElementTypes();
    if (context.reversedSubstitutions) {
      return matchTypeParameters(actualElementTypes, expectedElementTypes, context.resetSubstitutions());
    }
    else {
      return matchTypeParameters(expectedElementTypes, actualElementTypes, context);
    }
  }

  private static boolean matchTypeParameters(@NotNull List<PyType> expectedTypeParameters,
                                             @NotNull List<PyType> actualTypeParameters,
                                             @NotNull MatchContext context) {
    PyTypeParameterMapping mapping =
      PyTypeParameterMapping.mapByShape(expectedTypeParameters, actualTypeParameters);
    if (mapping == null) {
      return false;
    }
    for (Couple<PyType> pair : mapping.getMappedTypes()) {
      Optional<Boolean> matched = context.reversedSubstitutions
                                  ? match(pair.getSecond(), pair.getFirst(), context)
                                  : match(pair.getFirst(), pair.getSecond(), context);
      if (!matched.orElse(true)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchNumericTypes(PyType expected, PyType actual) {
    if (expected instanceof PyClassType && actual instanceof PyClassType) {
      final String superName = ((PyClassType)expected).getPyClass().getName();
      final String subName = ((PyClassType)actual).getPyClass().getName();
      final boolean subIsBool = "bool".equals(subName);
      final boolean subIsInt = PyNames.TYPE_INT.equals(subName);
      final boolean subIsLong = PyNames.TYPE_LONG.equals(subName);
      final boolean subIsFloat = "float".equals(subName);
      final boolean subIsComplex = "complex".equals(subName);
      if (superName == null || subName == null ||
          superName.equals(subName) ||
          (PyNames.TYPE_INT.equals(superName) && subIsBool) ||
          ((PyNames.TYPE_LONG.equals(superName) || PyNames.ABC_INTEGRAL.equals(superName)) && (subIsBool || subIsInt)) ||
          (("float".equals(superName) || PyNames.ABC_REAL.equals(superName)) && (subIsBool || subIsInt || subIsLong)) ||
          (("complex".equals(superName) || PyNames.ABC_COMPLEX.equals(superName)) && (subIsBool || subIsInt || subIsLong || subIsFloat)) ||
          (PyNames.ABC_NUMBER.equals(superName) && (subIsBool || subIsInt || subIsLong || subIsFloat || subIsComplex))) {
        return true;
      }
    }
    return false;
  }

  public static boolean isUnknown(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return isUnknown(type, true, context);
  }

  public static boolean isUnknown(@Nullable PyType type, boolean genericsAreUnknown, @NotNull TypeEvalContext context) {
    if (type == null || (genericsAreUnknown && type instanceof PyTypeParameterType)) {
      return true;
    }
    if (type instanceof PyFunctionType) {
      final PyCallable callable = ((PyFunctionType)type).getCallable();
      if (callable instanceof PyDecoratable &&
          PyKnownDecoratorUtil.hasChangingReturnTypeDecorator((PyDecoratable)callable, context)) {
        return true;
      }
    }
    if (type instanceof PyUnionType union) {
      for (PyType t : union.getMembers()) {
        if (isUnknown(t, genericsAreUnknown, context)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static GenericSubstitutions getSubstitutionsWithUnresolvedReturnGenerics(@NotNull Collection<PyCallableParameter> parameters,
                                                                                  @Nullable PyType returnType,
                                                                                  @Nullable GenericSubstitutions substitutions,
                                                                                  @NotNull TypeEvalContext context) {
    GenericSubstitutions existingSubstitutions = substitutions == null ? new GenericSubstitutions() : substitutions;
    Generics typeParamsFromReturnType = collectGenerics(returnType, context);
    // TODO Handle unmatched TypeVarTuples here as well
    if (typeParamsFromReturnType.typeVars.isEmpty() && typeParamsFromReturnType.paramSpecs.isEmpty()) {
      return existingSubstitutions;
    }
    Set<PyType> visited = new HashSet<>();
    Generics typeParamsFromParameterTypes = new Generics();
    for (PyCallableParameter parameter : parameters) {
      collectGenerics(parameter.getArgumentType(context), context, typeParamsFromParameterTypes, visited);
    }

    for (PyTypeVarType returnTypeParam : typeParamsFromReturnType.typeVars) {
      boolean canGetBoundFromArguments = typeParamsFromParameterTypes.typeVars.contains(returnTypeParam) ||
                                         typeParamsFromParameterTypes.typeVars.contains(invert(returnTypeParam));
      boolean isAlreadyBound = existingSubstitutions.typeVars.containsKey(returnTypeParam) ||
                               existingSubstitutions.typeVars.containsKey(invert(returnTypeParam));
      if (canGetBoundFromArguments && !isAlreadyBound) {
        existingSubstitutions.typeVars.put(returnTypeParam, null);
      }
    }
    for (PyParamSpecType paramSpecType : typeParamsFromReturnType.paramSpecs) {
      // ParamSpecs already bound to parameter lists 
      if (paramSpecType.getParameters() != null) {
        continue;
      }
      boolean canGetBoundFromArguments = typeParamsFromParameterTypes.paramSpecs.contains(paramSpecType);
      boolean isAlreadyBound = existingSubstitutions.paramSpecs.containsKey(paramSpecType);
      if (canGetBoundFromArguments && !isAlreadyBound) {
        existingSubstitutions.paramSpecs.put(paramSpecType, new PyParamSpecType(paramSpecType.getName())
          .withParameters(List.of(PyCallableParameterImpl.positionalNonPsi("args", null),
                                  PyCallableParameterImpl.keywordNonPsi("kwargs", null)),
                          context));
      }
    }
    return existingSubstitutions;
  }

  @NotNull
  private static <T extends PyInstantiableType<T>> T invert(@NotNull PyInstantiableType<T> instantiable) {
    return instantiable.isDefinition() ? instantiable.toInstance() : instantiable.toClass();
  }

  public static boolean hasGenerics(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return !collectGenerics(type, context).isEmpty();
  }

  @ApiStatus.Internal
  @NotNull
  public static Generics collectGenerics(@Nullable PyType type, @NotNull TypeEvalContext context) {
    final var result = new Generics();
    collectGenerics(type, context, result, new HashSet<>());
    return result;
  }

  private static void collectGenerics(@Nullable PyType type,
                                      @NotNull TypeEvalContext context,
                                      @NotNull Generics generics,
                                      @NotNull Set<? super PyType> visited) {
    if (type instanceof PyTypeParameterType typeParameter) {
      generics.allTypeParameters.add(typeParameter);
    }
    if (visited.contains(type)) {
      return;
    }
    visited.add(type);
    if (type instanceof PyTypeVarType typeVarType) {
      generics.typeVars.add(typeVarType);
    }
    if (type instanceof PyTypeVarTupleType typeVarTupleType) {
      generics.typeVarTuples.add(typeVarTupleType);
    }
    // TODO Filter out PyParamSpecTypes representing actual lists of parameters, not type parameters declared via ParamSpec
    if (type instanceof PyParamSpecType) {
      generics.paramSpecs.add((PyParamSpecType)type);
    }
    // TODO These are not type parameters at all
    if (type instanceof PyConcatenateType) {
      generics.concatenates.add((PyConcatenateType)type);
    }
    if (type instanceof PySelfType) {
      generics.self = (PySelfType)type;
    }
    else if (type instanceof PyUnionType union) {
      for (PyType t : union.getMembers()) {
        collectGenerics(t, context, generics, visited);
      }
    }
    else if (type instanceof PyTupleType tuple) {
      final int n = tuple.isHomogeneous() ? 1 : tuple.getElementCount();
      for (int i = 0; i < n; i++) {
        collectGenerics(tuple.getElementType(i), context, generics, visited);
      }
    }
    else if (type instanceof PyCollectionType collection) {
      for (PyType elementType : collection.getElementTypes()) {
        collectGenerics(elementType, context, generics, visited);
      }
    }
    else if (type instanceof PyCallableType callable && !(type instanceof PyClassLikeType)) {
      final List<PyCallableParameter> parameters = callable.getParameters(context);
      if (parameters != null) {
        for (PyCallableParameter parameter : parameters) {
          if (parameter != null) {
            collectGenerics(parameter.getType(context), context, generics, visited);
          }
        }
      }
      collectGenerics(callable.getReturnType(context), context, generics, visited);
    }
    else if (type instanceof PyUnpackedTupleType unpackedTupleType) {
      for (PyType elementType : unpackedTupleType.getElementTypes()) {
        collectGenerics(elementType, context, generics, visited);
      }
    }
  }

  public static @NotNull List<@Nullable PyType> substituteExpand(@Nullable PyType type,
                                                                 @NotNull GenericSubstitutions substitutions,
                                                                 @NotNull TypeEvalContext context,
                                                                 @NotNull Set<PyType> substituting) {
    PyType substituted = substitute(type, substitutions, context, substituting);
    if (substituted instanceof PyUnpackedTupleType unpackedTupleType && !unpackedTupleType.isUnbound()) {
      return unpackedTupleType.getElementTypes();
    }
    return Collections.singletonList(substituted);
  }

  @Nullable
  public static PyType substitute(@Nullable PyType type, @NotNull GenericSubstitutions substitutions, @NotNull TypeEvalContext context) {
    return substitute(type, substitutions, context, new HashSet<>());
  }

  @Nullable
  public static PyType substitute(@Nullable PyType type,
                                  @NotNull GenericSubstitutions substitutions,
                                  @NotNull TypeEvalContext context,
                                  @NotNull Set<PyType> substituting) {
    boolean alreadySubstituting = !substituting.add(type);
    if (alreadySubstituting) {
      return null;
    }
    try {
      if (hasGenerics(type, context)) {
        if (type instanceof PyUnpackedTupleType unpackedTupleType) {
          return new PyUnpackedTupleTypeImpl(ContainerUtil.flatMap(unpackedTupleType.getElementTypes(),
                                                                   t -> substituteExpand(t, substitutions, context, substituting)),
                                             unpackedTupleType.isUnbound());
        }
        if (type instanceof PyTypeVarTupleType typeVarTupleType) {
          if (!substitutions.typeVarTuples.containsKey(typeVarTupleType)) {
            return type;
          }
          PyVariadicType substitution = substitutions.typeVarTuples.get(typeVarTupleType);
          if (!typeVarTupleType.equals(substitution) && hasGenerics(substitution, context)) {
            return substitute(substitution, substitutions, context, substituting);
          }
          // TODO This should happen in getSubstitutionsWithUnresolvedReturnGenerics, but it won't work for inherited constructors
          //   as in testVariadicGenericCheckTypeAliasesRedundantParameter, investigate why
          // Replace unknown TypeVarTuples by *tuple[Any, ...] instead of plain Any
          return substitution == null ? PyUnpackedTupleTypeImpl.UNSPECIFIED : substitution;
        }
        if (type instanceof PyTypeVarType typeVar) {
          // Both mappings of kind {T: T2} (and no mapping for T2) and {T: T} mean the substitution process should stop for T.
          // The first one occurs in the situations like
          //
          // def f(x: T1) -> T1: ...
          // def g(y: T2): f(y)
          //
          // when we're inferring the return type of "g".
          // The second is a plug for type hints like
          //
          // def g() -> Callable[[T], T]
          //
          // where we want to prevent replacing T with Any, even though it cannot be inferred at a call site.
          if (!substitutions.typeVars.containsKey(typeVar) && !substitutions.typeVars.containsKey(invert(typeVar))) {
            return typeVar;
          }
          PyType substitution = substitutions.typeVars.get(typeVar);
          if (substitution == null) {
            final PyInstantiableType<?> invertedTypeVar = invert(typeVar);
            final PyInstantiableType<?> invertedSubstitution = as(substitutions.typeVars.get(invertedTypeVar), PyInstantiableType.class);
            if (invertedSubstitution != null) {
              substitution = invert(invertedSubstitution);
            }
          }
          // TODO remove !typeVar.equals(substitution) part, it's necessary due to the logic in unifyReceiverWithParamSpecs
          if (!typeVar.equals(substitution) && hasGenerics(substitution, context)) {
            return substitute(substitution, substitutions, context, substituting);
          }
          return substitution;
        }
        else if (type instanceof PyParamSpecType paramSpecType && paramSpecType.getParameters() == null) {
          if (!substitutions.paramSpecs.containsKey(paramSpecType)) {
            return paramSpecType;
          }
          PyParamSpecType substitution = substitutions.paramSpecs.get(paramSpecType);
          if (substitution != null && !substitution.equals(paramSpecType) && hasGenerics(substitution, context)) {
            return substitute(substitution, substitutions, context);
          }
          // TODO For ParamSpecs, replace Any with (*args: Any, **kwargs: Any) as it's a logical "wildcard" for this kind of type parameter
          return substitution;
        }
        else if (type instanceof PySelfType) {
          var qualifierType = substitutions.qualifierType;
          var selfScopeClassType = ((PySelfType)type).getScopeClassType();
          return PyTypeUtil.toStream(qualifierType)
            .filter(memberType -> match(selfScopeClassType, memberType, context))
            .collect(PyTypeUtil.toUnion());
        }
        else if (type instanceof PyUnionType) {
          return ((PyUnionType)type).map(member -> substitute(member, substitutions, context, substituting));
        }
        else if (type instanceof PyTypedDictType typedDictType) {
          final Map<String, kotlin.Pair<PyExpression, PyType>> tdFields = typedDictType.getKeysToValuesWithTypes();
          final var substitutedTDFields = ContainerUtil.map2Map(
            tdFields.entrySet(),
            field -> Pair.create(
              field.getKey(),
              new kotlin.Pair<>(
                field.getValue().getFirst(),
                substitute(field.getValue().getSecond(), substitutions, context, substituting)
              )
            )
          );
          return PyTypedDictType.Companion.createFromKeysToValueTypes(typedDictType.myClass, substitutedTDFields, false);
        }
        else if (type instanceof final PyCollectionTypeImpl collection) {
          return new PyCollectionTypeImpl(collection.getPyClass(), collection.isDefinition(),
                                          ContainerUtil.flatMap(collection.getElementTypes(),
                                                                t -> substituteExpand(t, substitutions, context, substituting)));
        }
        else if (type instanceof PyTupleType tupleType) {
          final PyClass tupleClass = tupleType.getPyClass();

          final List<PyType> oldElementTypes = tupleType.isHomogeneous()
                                               ? Collections.singletonList(tupleType.getIteratedItemType())
                                               : tupleType.getElementTypes();

          // newElementTypes need to be modifiable list
          final List<PyType> newElementTypes =
            new ArrayList<>(ContainerUtil.flatMap(oldElementTypes, elementType ->
              substituteExpand(elementType, substitutions, context, substituting)));

          return new PyTupleType(tupleClass, newElementTypes, tupleType.isHomogeneous());
        }
        else if (type instanceof PyCallableType callable && !(type instanceof PyClassLikeType)) {
          List<PyCallableParameter> substParams;
          List<PyCallableParameter> parameters = callable.getParameters(context);
          if (parameters != null) {
            PyCallableParameter onlyParam = ContainerUtil.getOnlyItem(parameters);
            if (onlyParam != null && onlyParam.getType(context) instanceof PyParamSpecType paramSpecType) {
              final var substitution = substitute(paramSpecType, substitutions, context);
              if (substitution instanceof PyParamSpecType paramSpecSubst && paramSpecSubst.getParameters() != null) {
                substParams = paramSpecSubst.getParameters();
              }
              else {
                substParams = List.of(PyCallableParameterImpl.nonPsi(substitution));
              }
            }
            else if (onlyParam != null && onlyParam.getType(context) instanceof PyConcatenateType concatenateType) {
              final var paramSpecSubst = as(substitute(concatenateType.getParamSpec(), substitutions, context), PyParamSpecType.class);
              List<PyCallableParameter> paramSpecParams = paramSpecSubst != null ? paramSpecSubst.getParameters() : null;
              substParams = StreamEx.of(concatenateType.getFirstTypes())
                .flatCollection(paramType -> substituteExpand(paramType, substitutions, context, substituting))
                .map(PyCallableParameterImpl::nonPsi)
                .append(paramSpecParams != null ? paramSpecParams :
                        Collections.singletonList(PyCallableParameterImpl.nonPsi(paramSpecSubst)))
                .toList();
            }
            else {
              substParams = StreamEx.of(parameters)
                .mapToEntry(param -> param.getType(context))
                .flatMapKeyValue((param, paramType) -> {
                  PyParameter paramPsi = param.getParameter();
                  return StreamEx.of(substituteExpand(param.getType(context), substitutions, context, substituting))
                    .map(paramSubType -> paramPsi != null ?
                                         PyCallableParameterImpl.psi(paramPsi, paramSubType) :
                                         PyCallableParameterImpl.nonPsi(param.getName(), paramSubType, param.getDefaultValue()));
                })
                .toList();
            }
          }
          else {
            substParams = null;
          }
          final PyType substResult = substitute(callable.getReturnType(context), substitutions, context, substituting);
          return new PyCallableTypeImpl(substParams, substResult);
        }
      }
    }
    finally {
      substituting.remove(type);
    }
    return type;
  }

  @Nullable
  public static GenericSubstitutions unifyGenericCall(@Nullable PyExpression receiver,
                                                      @NotNull Map<PyExpression, PyCallableParameter> arguments,
                                                      @NotNull TypeEvalContext context) {
    // UnifyReceiver retains the information about type parameters of ancestor generic classes,
    // which might be necessary if a method being called is defined in one them, not in the actual
    // class of the receiver. In theory, it could be replaced by matching the type of self parameter
    // with the receiver uniformly with the rest of the arguments.
    final var substitutions = unifyReceiver(receiver, context);
    for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(arguments).entrySet()) {
      final PyCallableParameter paramWrapper = entry.getValue();
      final PyType expectedType = paramWrapper.getArgumentType(context);
      final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(entry.getKey(), expectedType, context, substitutions);
      var actualType = promotedToLiteral != null ? promotedToLiteral : context.getType(entry.getKey());
      // Matching with the type of "self" is necessary in particular for choosing the most specific overloads, e.g.
      // LiteralString-specific methods of str, or for instantiating the type parameters of the containing class
      // when it's not possible to infer them by other means, e.g. as in the following overload of dict[_KT, _VT].__init__:
      // def __init__(self: dict[str, _VT], **kwargs: _VT) -> None: ...
      if (paramWrapper.isSelf()) {
        // TODO find out a better way to pass the corresponding function inside
        final PyParameter param = paramWrapper.getParameter();
        final PyFunction function = as(ScopeUtil.getScopeOwner(param), PyFunction.class);
        assert function != null;
        if (isNewMethod(function) || function.getModifier() == PyAstFunction.Modifier.CLASSMETHOD) {
          actualType = PyTypeUtil.toStream(actualType)
            .select(PyClassLikeType.class)
            .map(PyClassLikeType::toClass)
            .select(PyType.class)
            .foldLeft(PyUnionType::union)
            .orElse(actualType);
        }
        else if (PyUtil.isInitMethod(function)) {
          actualType = PyTypeUtil.toStream(actualType)
            .select(PyInstantiableType.class)
            .map(PyInstantiableType::toInstance)
            .select(PyType.class)
            .foldLeft(PyUnionType::union)
            .orElse(actualType);
        }

        PyClass containingClass = function.getContainingClass();
        assert containingClass != null;
        PyType genericClass = findGenericDefinitionType(containingClass, context);
        if (genericClass instanceof PyInstantiableType<?> instantiableType && (isNewMethod(function) || function.getModifier() == PyAstFunction.Modifier.CLASSMETHOD)) {
          genericClass = instantiableType.toClass();
        }
        if (genericClass != null && !match(genericClass, expectedType, context, substitutions)) {
          return null;
        }
      }
      if (!match(expectedType, actualType, context, substitutions)) {
        return null;
      }
    }
    if (!matchContainer(getMappedPositionalContainer(arguments), getArgumentsMappedToPositionalContainer(arguments),
                        substitutions, context)) {
      return null;
    }
    if (!matchContainer(getMappedKeywordContainer(arguments), getArgumentsMappedToKeywordContainer(arguments),
                        substitutions, context)) {
      return null;
    }
    return substitutions;
  }

  private static boolean matchContainer(@Nullable PyCallableParameter container, @NotNull List<? extends PyExpression> arguments,
                                        @NotNull GenericSubstitutions substitutions, @NotNull TypeEvalContext context) {
    if (container == null) {
      return true;
    }
    final List<PyType> actualArgumentTypes = ContainerUtil.map(arguments, context::getType);
    final PyType expectedArgumentType = container.getArgumentType(context);
    if (container.isPositionalContainer() && expectedArgumentType instanceof PyVariadicType variadicType) {
      return match(variadicType, PyUnpackedTupleTypeImpl.create(actualArgumentTypes),
                   new MatchContext(context, substitutions, false));
    }
    return match(expectedArgumentType, PyUnionType.union(actualArgumentTypes), context, substitutions);
  }

  @NotNull
  public static GenericSubstitutions unifyReceiver(@Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    // Collect generic params of object type
    final var substitutions = new GenericSubstitutions();
    if (receiver != null) {
      // TODO properly handle union types here
      PyType receiverType = context.getType(receiver);
      if (receiverType instanceof PyClassType) {
        substitutions.qualifierType = ((PyClassType)receiverType).toInstance();
      }
      else {
        substitutions.qualifierType = receiverType;
      }
      PyTypeUtil.toStream(receiverType)
        .select(PyClassType.class)
        .map(type -> collectTypeSubstitutions(type, context))
        .forEach(newSubstitutions -> {
          for (Map.Entry<PyTypeVarType, PyType> typeVarMapping : newSubstitutions.typeVars.entrySet()) {
            substitutions.typeVars.putIfAbsent(typeVarMapping.getKey(), typeVarMapping.getValue());
          }
          for (Map.Entry<PyTypeVarTupleType, PyVariadicType> typeVarMapping : newSubstitutions.typeVarTuples.entrySet()) {
            substitutions.typeVarTuples.putIfAbsent(typeVarMapping.getKey(), typeVarMapping.getValue());
          }
          for (Map.Entry<PyParamSpecType, PyParamSpecType> paramSpecMapping : newSubstitutions.paramSpecs.entrySet()) {
            substitutions.paramSpecs.putIfAbsent(paramSpecMapping.getKey(), paramSpecMapping.getValue());
          }
        });
    }
    return substitutions;
  }

  private static boolean matchClasses(@Nullable PyClass superClass, @Nullable PyClass subClass, @NotNull TypeEvalContext context) {
    if (superClass == null ||
        subClass == null ||
        subClass.isSubclass(superClass, context) ||
        PyABCUtil.isSubclass(subClass, superClass, context) ||
        isStrUnicodeMatch(subClass, superClass) ||
        isBytearrayBytesStringMatch(subClass, superClass) ||
        PyUtil.hasUnresolvedAncestors(subClass, context)) {
      return true;
    }
    else {
      final String superName = superClass.getName();
      return superName != null && superName.equals(subClass.getName());
    }
  }

  private static boolean isStrUnicodeMatch(@NotNull PyClass subClass, @NotNull PyClass superClass) {
    // TODO: Check for subclasses as well
    return PyNames.TYPE_STR.equals(subClass.getName()) && PyNames.TYPE_UNICODE.equals(superClass.getName());
  }

  private static boolean isBytearrayBytesStringMatch(@NotNull PyClass subClass, @NotNull PyClass superClass) {
    if (!PyNames.TYPE_BYTEARRAY.equals(subClass.getName())) return false;

    final PsiFile subClassFile = subClass.getContainingFile();

    final boolean isPy2 = subClassFile instanceof PyiFile
                          ? PythonRuntimeService.getInstance().getLanguageLevelForSdk(PythonSdkUtil.findPythonSdk(subClassFile)).isPython2()
                          : LanguageLevel.forElement(subClass).isPython2();

    final String superClassName = superClass.getName();
    return isPy2 && PyNames.TYPE_STR.equals(superClassName) || !isPy2 && PyNames.TYPE_BYTES.equals(superClassName);
  }

  @Nullable
  public static Boolean isCallable(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    if (type instanceof PyUnionType) {
      return isUnionCallable((PyUnionType)type);
    }
    if (type instanceof PyCallableType) {
      return ((PyCallableType)type).isCallable();
    }
    if (type instanceof PyStructuralType && ((PyStructuralType)type).isInferredFromUsages()) {
      return true;
    }
    if (type instanceof PyTypeVarType typeVarType) {
      if (typeVarType.isDefinition()) {
        return true;
      }

      return isCallable(typeVarType.getBound());
    }
    return false;
  }

  /**
   * If at least one is callable -- it is callable.
   * If at least one is unknown -- it is unknown.
   * It is false otherwise.
   */
  @Nullable
  private static Boolean isUnionCallable(@NotNull final PyUnionType type) {
    for (final PyType member : type.getMembers()) {
      final Boolean callable = isCallable(member);
      if (callable == null) {
        return null;
      }
      if (callable) {
        return true;
      }
    }
    return false;
  }

  public static boolean definesGetAttr(@NotNull PyFile file, @NotNull TypeEvalContext context) {
    if (file instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)file);
      if (type != null) {
        return resolveTypeMember(type, PyNames.GETATTR, context) != null;
      }
    }

    return false;
  }

  public static boolean overridesGetAttr(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyType type = context.getType(cls);
    if (type != null) {
      if (resolveTypeMember(type, PyNames.GETATTR, context) != null) {
        return true;
      }
      final PsiElement method = resolveTypeMember(type, PyNames.GETATTRIBUTE, context);
      if (method != null && !PyBuiltinCache.getInstance(cls).isBuiltin(method)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement resolveTypeMember(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
    return !ContainerUtil.isEmpty(results) ? results.get(0).getElement() : null;
  }

  @Nullable
  public static PyType getTargetTypeFromTupleAssignment(@NotNull PyExpression target,
                                                        @NotNull PySequenceExpression parentTupleOrList,
                                                        @NotNull PyTupleType assignedTupleType) {
    final int count = assignedTupleType.getElementCount();
    final PyExpression[] elements = parentTupleOrList.getElements();
    if (elements.length == count || assignedTupleType.isHomogeneous()) {
      final int index = ArrayUtil.indexOf(elements, target);
      if (index >= 0) {
        return assignedTupleType.getElementType(index);
      }
      for (int i = 0; i < count; i++) {
        PyExpression element = PyPsiUtils.flattenParens(elements[i]);
        if (element instanceof PyTupleExpression || element instanceof PyListLiteralExpression) {
          final PyType elementType = assignedTupleType.getElementType(i);
          if (elementType instanceof PyTupleType nestedAssignedTupleType) {
            final PyType result = getTargetTypeFromTupleAssignment(target, (PySequenceExpression)element, nestedAssignedTupleType);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Populates an existing generic type with given actual type parameters in their original order.
   *
   * @return a parameterized version of the original type or {@code null} if it cannot be meaningfully parameterized.
   */
  @ApiStatus.Internal
  @Nullable
  public static PyType parameterizeType(@NotNull PyType genericType,
                                        @NotNull List<PyType> actualTypeParams,
                                        @NotNull TypeEvalContext context) {
    Generics typeParams = collectGenerics(genericType, context);
    if (!typeParams.isEmpty()) {
      var substitutions = new GenericSubstitutions();
      List<PyType> expectedTypeParams = new ArrayList<>(new LinkedHashSet<>(typeParams.getAllTypeParameters()));
      PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(expectedTypeParams, actualTypeParams, Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
      if (mapping != null) {
        for (Couple<PyType> pair : mapping.getMappedTypes()) {
          if (pair.getFirst() instanceof PyTypeVarType typeVar) {
            substitutions.typeVars.put(typeVar, pair.getSecond());
          }
          else if (pair.getFirst() instanceof PyTypeVarTupleType typeVarTuple) {
            assert pair.getSecond() instanceof PyVariadicType;
            substitutions.typeVarTuples.put(typeVarTuple, (PyVariadicType)pair.getSecond());
          }
        }
      }
      return substitute(genericType, substitutions, context);
    }
    // An already parameterized type, don't override existing values for type parameters
    else if (genericType instanceof PyCollectionType) {
      return genericType;
    }
    else if (genericType instanceof PyClassType) {
      PyClass cls = ((PyClassType)genericType).getPyClass();
      return new PyCollectionTypeImpl(cls, false, actualTypeParams);
    }
    else {
      return null;
    }
  }

  @ApiStatus.Internal
  public static class Generics {
    @NotNull
    private final Set<PyTypeVarType> typeVars = new LinkedHashSet<>();

    @NotNull
    private final Set<PyTypeVarTupleType> typeVarTuples = new LinkedHashSet<>();

    @NotNull
    private final List<PyTypeParameterType> allTypeParameters = new ArrayList<>();

    @NotNull
    private final Set<PyParamSpecType> paramSpecs = new LinkedHashSet<>();

    @NotNull
    private final Set<PyConcatenateType> concatenates = new LinkedHashSet<>();

    @Nullable
    private PySelfType self;

    public @NotNull Set<PyTypeVarType> getTypeVars() {
      return Collections.unmodifiableSet(typeVars);
    }

    public @NotNull Set<PyTypeVarTupleType> getTypeVarTuples() {
      return Collections.unmodifiableSet(typeVarTuples);
    }

    public @NotNull List<PyTypeParameterType> getAllTypeParameters() {
      return Collections.unmodifiableList(allTypeParameters);
    }

    public @NotNull Set<PyParamSpecType> getParamSpecs() {
      return Collections.unmodifiableSet(paramSpecs);
    }

    public boolean isEmpty() {
      return typeVars.isEmpty() && typeVarTuples.isEmpty() && paramSpecs.isEmpty() && concatenates.isEmpty() && self == null;
    }

    @Override
    public String toString() {
      return "Generics{" +
             "typeVars=" + typeVars +
             ", typeVarTuples" + typeVarTuples +
             ", paramSpecs=" + paramSpecs +
             '}';
    }
  }

  @ApiStatus.Experimental
  public static class GenericSubstitutions {
    @NotNull
    private final Map<PyTypeVarType, PyType> typeVars;

    @NotNull
    private final Map<PyTypeVarTupleType, PyVariadicType> typeVarTuples;

    @NotNull
    private final Map<PyParamSpecType, PyParamSpecType> paramSpecs;

    @Nullable
    private PyType qualifierType;

    public GenericSubstitutions(@NotNull Map<? extends PyTypeParameterType, PyType> typeParameters) {
      this(
        EntryStream.of(typeParameters)
          .selectKeys(PyTypeVarType.class)
          .toCustomMap(LinkedHashMap::new),
        EntryStream.of(typeParameters)
          .selectKeys(PyTypeVarTupleType.class)
          .selectValues(PyVariadicType.class)
          .toCustomMap(LinkedHashMap::new),
        EntryStream.of(typeParameters)
          .selectKeys(PyParamSpecType.class)
          .selectValues(PyParamSpecType.class)
          .toCustomMap(LinkedHashMap::new),
        null
      );
    }

    public GenericSubstitutions() {
      this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), null);
    }

    private GenericSubstitutions(@NotNull Map<PyTypeVarType, PyType> typeVars,
                                 @NotNull Map<PyTypeVarTupleType, PyVariadicType> typeVarTuples,
                                 @NotNull Map<PyParamSpecType, PyParamSpecType> paramSpecs,
                                 @Nullable PyType qualifierType) {
      this.typeVars = typeVars;
      this.typeVarTuples = typeVarTuples;
      this.paramSpecs = paramSpecs;
      this.qualifierType = qualifierType;
    }

    public @NotNull Map<PyParamSpecType, PyParamSpecType> getParamSpecs() {
      return Collections.unmodifiableMap(paramSpecs);
    }

    public @NotNull Map<PyTypeVarType, PyType> getTypeVars() {
      return Collections.unmodifiableMap(typeVars);
    }

    public @NotNull Map<PyTypeVarTupleType, PyVariadicType> getTypeVarTuples() {
      return Collections.unmodifiableMap(typeVarTuples);
    }

    @Nullable
    public PyType getQualifierType() {
      return qualifierType;
    }

    @Override
    public String toString() {
      return "GenericSubstitutions{" +
             "typeVars=" + typeVars +
             ", typeVarTuples" + typeVarTuples +
             ", paramSpecs=" + paramSpecs +
             '}';
    }
  }

  private static class MatchContext {

    @NotNull
    private final TypeEvalContext context;

    @NotNull
    private final GenericSubstitutions mySubstitutions;

    private final boolean reversedSubstitutions;

    MatchContext(@NotNull TypeEvalContext context, @NotNull GenericSubstitutions substitutions, boolean reversedSubstitutions) {
      this.context = context;
      this.mySubstitutions = substitutions;
      this.reversedSubstitutions = reversedSubstitutions;
    }

    @NotNull
    public MatchContext reverseSubstitutions() {
      return new MatchContext(context, mySubstitutions, !reversedSubstitutions);
    }

    public @NotNull MatchContext resetSubstitutions() {
      return new MatchContext(context, mySubstitutions, false);
    }
  }
}
