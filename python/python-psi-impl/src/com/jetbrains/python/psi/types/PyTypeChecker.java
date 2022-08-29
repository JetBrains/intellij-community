// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.sdk.PythonSdkUtil;
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
    return match(expected, actual, getMatchContext(context, new GenericSubstitutions())).orElse(true);
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
                              @NotNull Map<PyGenericType, PyType> typeVars) {
    var substitutions = new GenericSubstitutions(typeVars, new HashMap<>(), new HashMap<>(), null);
    return match(expected, actual, getMatchContext(context, substitutions)).orElse(true);
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
      final Optional<Boolean> result = extension.match(expected, actual, context.context, context.mySubstitutions.typeVars);
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

    if (expected instanceof PyGenericVariadicType) {
      return Optional.of(match((PyGenericVariadicType)expected, actual, context));
    }

    if (actual instanceof PyGenericType && context.reversedSubstitutions) {
      return Optional.of(match((PyGenericType)actual, expected, context));
    }

    if (expected instanceof PyGenericType) {
      return Optional.of(match((PyGenericType)expected, actual, context));
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
   * Match {@code actual} versus {@code PyGenericType expected}.
   *
   * The method mutates {@code context.substitutions} map adding new entries into it
   */
  private static boolean match(@NotNull PyGenericType expected, @Nullable PyType actual, @NotNull MatchContext context) {
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

    Optional<Boolean> match = match(bound, actual, context);
    if (match.isPresent() && !match.get()) {
      return false;
    }

    if (substitution != null) {
      if (expected.equals(actual) || substitution.equals(expected)) {
        return true;
      }

      Optional<Boolean> recursiveMatch = RecursionManager.doPreventingRecursion(
        expected, false, context.reversedSubstitutions
                         ? () -> match(actual, substitution, context)
                         : () -> match(substitution, actual, context)
      );
      return recursiveMatch != null ? recursiveMatch.orElse(false) : false;
    }

    if (actual != null) {
      context.mySubstitutions.typeVars.put(expected, actual);
    }
    else if (bound != null) {
      context.mySubstitutions.typeVars.put(expected, PyUnionType.createWeakType(bound));
    }

    return true;
  }

  private static boolean match(@NotNull PyGenericVariadicType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    if (actual instanceof final PyGenericVariadicType actualGenericVariadic) {
      if (expected.isMapped(context.mySubstitutions.typeVarTuples) || actualGenericVariadic.isMapped(context.mySubstitutions.typeVarTuples)) {
        return matchElementTypes(List.of(expected), List.of(actualGenericVariadic), context, false, true);
      }

      if (expected.isHomogeneous() && actualGenericVariadic.isHomogeneous()) {
        return match(expected.getIteratedItemType(), actualGenericVariadic.getIteratedItemType(), context).orElse(false);
      }

      if (!actualGenericVariadic.isHomogeneous() && context.reversedSubstitutions) {
        context.mySubstitutions.typeVarTuples.put(actualGenericVariadic, expected);
      }
      if (!expected.isHomogeneous() && !context.reversedSubstitutions) {
        context.mySubstitutions.typeVarTuples.put(expected, actualGenericVariadic);
      }
      return true;
    }

    if (actual instanceof PyUnionType) {
      return false;
    }

    if (expected.isHomogeneous()) {
      return match(expected.getIteratedItemType(), actual, context).orElse(false);
    }

    var elementTypes = new ArrayList<PyType>();
    elementTypes.add(actual);
    context.mySubstitutions.typeVarTuples.put(expected, expected.withElementTypes(false, elementTypes));
    return true;
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
    if (expected instanceof PyTupleType) {
      Optional<Boolean> match = match((PyTupleType)expected, actual, context);
      if (match.isPresent()) {
        return match.get();
      }
    }

    if (ContainerUtil.exists(actual.getMembers(), x -> x instanceof PyLiteralStringType)) { // checking strictly separately until PY-24834 gets implemented
      return ContainerUtil.and(actual.getMembers(), type -> match(expected, type, context).orElse(false));
    }

    return ContainerUtil.or(actual.getMembers(), type -> match(expected, type, context).orElse(false));
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyTupleType expected, @NotNull PyUnionType actual, @NotNull MatchContext context) {
    final int elementCount = expected.getElementCount();

    if (!expected.isHomogeneous() && consistsOfSameElementNumberTuples(actual, elementCount)) {
      return Optional.of(substituteExpectedElementsWithUnions(expected, elementCount, actual, context));
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
  public static List<PyType> substituteGenericVariadicInElementTypes(@NotNull List<PyType> elementTypes, @NotNull MatchContext context) {
    var genericVariadics =
      ContainerUtil.findAll(elementTypes, it -> it instanceof PyGenericVariadicType &&
                                                ((PyGenericVariadicType)it).isMapped(context.mySubstitutions.typeVarTuples));
    if (genericVariadics.isEmpty()) {
      return elementTypes;
    }

    List<PyType> result = new ArrayList<>();
    for (var elementType: elementTypes) {
      if (elementType instanceof PyGenericVariadicType) {
        var substitution = ((PyGenericVariadicType)elementType).getMappedElementTypes(context.mySubstitutions.typeVarTuples);
        if (substitution != null) {
          substitution = substituteGenericVariadicInElementTypes(substitution, context);
          result.addAll(substitution);
        }
      }
      else {
        result.add(elementType);
      }
    }

    return result;
  }

  @NotNull
  private static List<PyType> getVariadicGenerics(@NotNull List<PyType> elementTypes) {
    return ContainerUtil.findAll(elementTypes, it -> it instanceof PyGenericVariadicType);
  }

  public static boolean matchElementTypes(@NotNull List<PyType> expectedElementTypes, @NotNull List<PyType> actualElementTypes,
                                           @NotNull MatchContext context, boolean exactMatch, boolean collectEqualsTypeVars) {
    return matchElementTypes(expectedElementTypes, actualElementTypes, context, exactMatch, false, collectEqualsTypeVars,
                             new ArrayList<>());
  }

  private static boolean matchRestExpectedTypesWithEmptyActualTypes(@NotNull List<PyType> expectedElementTypes,
                                   @NotNull MatchContext context, boolean exactMatch, boolean reversed,
                                   @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult,
                                   @Nullable PyType expectedElementType, int iExp, int expectedSize) {
    if (expectedElementType instanceof final PyGenericVariadicType genericVariadicExpectedType &&
        (!exactMatch || iExp == expectedSize - 1)) {
      context.mySubstitutions.typeVarTuples
        .put(genericVariadicExpectedType, genericVariadicExpectedType.withElementTypes(false, Collections.emptyList()));
      return true;
    }
    if (expectedElementType instanceof final PyParamSpecType paramSpecType) {
      if (!context.mySubstitutions.paramSpecs.containsKey(paramSpecType)) {
        context.mySubstitutions.paramSpecs.put(paramSpecType, paramSpecType.withParameters(Collections.emptyList(), context.context));
      }
      return true;
    }

    var restTypes = new ArrayList<PyType>();
    while (iExp < expectedSize) {
      restTypes.add(expectedElementTypes.get(iExp));
      iExp++;
    }
    var restGenericVariadic = PyGenericVariadicType.fromElementTypes(restTypes);
    if (reversed) {
      matchingResult.add(new Trinity<>(restGenericVariadic, PyNotMatchedType.INSTANCE, false));
    }
    else {
      matchingResult.add(new Trinity<>(PyNotMatchedType.INSTANCE, restGenericVariadic, false));
    }
    return !exactMatch;
  }

  private static boolean matchRestActualTypesWithEmptyExpectedTypes(@NotNull List<PyType> actualElementTypes, boolean exactMatch,
                                                                    boolean reversed,
                                                                    @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult,
                                                                    int iAct, int actualSize) {
    boolean flagIteration = false;
    while (iAct < actualSize) {
      flagIteration = true;
      if (!reversed) {
        matchingResult.add(new Trinity<>(actualElementTypes.get(iAct), PyNotMatchedType.INSTANCE, false));
      }
      else {
        matchingResult.add(new Trinity<>(PyNotMatchedType.INSTANCE, actualElementTypes.get(iAct), false));
      }
      iAct++;
    }
    return !flagIteration || !exactMatch;
  }

  private static boolean matchNonGenericVariadics(@Nullable PyType expectedElementType, @Nullable PyType actualElementType,
                                                  @NotNull MatchContext context, boolean reversed, boolean collectEqualsTypeVars,
                                                  @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult) {
    var expected = reversed ? actualElementType : expectedElementType;
    var actual = reversed ? expectedElementType : actualElementType;
    var matched = match(expected, actual, context).orElse(true);

    if (collectEqualsTypeVars && expected instanceof PyGenericType && expected.equals(actual) &&
        !context.mySubstitutions.typeVars.containsKey(expected)) {
      context.mySubstitutions.typeVars.put((PyGenericType)expected, actual);
    }

    matchingResult.add(new Trinity<>(actual, expected, matched));

    return matched;
  }

  private static boolean matchGenericVariadicWithElementTypes(@NotNull PyGenericVariadicType genericVariadicExpectedType,
                                                              @NotNull List<PyType> actualElementTypes,
                                                              @NotNull MatchContext context, boolean reversed,
                                                              @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult,
                                                              int suffixExpSize, int suffixActSize, int iAct) {
    if (suffixActSize < suffixExpSize) {
      return false;
    }

    List<PyType> actualElementTypesSublist = new ArrayList<>();
    for (int i = iAct; i < iAct + suffixActSize - suffixExpSize; i++) {
      actualElementTypesSublist.add(actualElementTypes.get(i));
    }

    return matchGenericVariadicWithElementTypes(genericVariadicExpectedType, actualElementTypesSublist, context, reversed, matchingResult);
  }

  /**
   * Match two lists of types with expanding generic variadics
   *
   * {@code reversed} flag is to consider only {@code PyGenericVariadicType} in {@code expectedElementTypes} after all substitutions
   *
   * {@code exactMatch} flag is either exact match types or possibly match with nulls and not check element counts
   */
  public static boolean matchElementTypes(@NotNull List<PyType> expectedElementTypes, @NotNull List<PyType> actualElementTypes,
                                          @NotNull MatchContext context, boolean exactMatch, boolean reversed,
                                          boolean collectEqualsTypeVars,
                                          @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult) {
    expectedElementTypes = substituteGenericVariadicInElementTypes(expectedElementTypes, context);
    actualElementTypes = substituteGenericVariadicInElementTypes(actualElementTypes, context);

    var matchedBoth = tryMatchElementTypesBothGenericVariadic(expectedElementTypes, actualElementTypes, context, exactMatch, reversed,
                                                              collectEqualsTypeVars, matchingResult);
    if (matchedBoth.isPresent()) return matchedBoth.get();

    boolean allMatched = true;
    int expectedSize = expectedElementTypes.size();
    int actualSize = actualElementTypes.size();
    int iExp = 0;
    int iAct = 0;
    while (iExp < expectedSize) {
      PyType expectedElementType = expectedElementTypes.get(iExp);
      PyType actualElementType;
      if (iAct >= actualSize) {
        return matchRestExpectedTypesWithEmptyActualTypes(expectedElementTypes, context, exactMatch, reversed, matchingResult,
                                                          expectedElementType, iExp, expectedSize);
      }
      else {
        actualElementType = actualElementTypes.get(iAct);
      }

      if (expectedElementType instanceof PyParamSpecType) {
        context.mySubstitutions.paramSpecs.put((PyParamSpecType)expectedElementType, as(actualElementType, PyParamSpecType.class));
        iExp++;
        iAct++;
        continue;
      }

      if (!(expectedElementType instanceof PyGenericVariadicType)) {
        allMatched &= matchNonGenericVariadics(expectedElementType, actualElementType, context, reversed, collectEqualsTypeVars,
                                               matchingResult);
        iExp++;
        iAct++;
        continue;
      }

      int suffixExpSize = expectedSize - iExp - 1;
      int suffixActSize = actualSize - iAct;
      allMatched &= matchGenericVariadicWithElementTypes((PyGenericVariadicType)expectedElementType, actualElementTypes, context, reversed,
                                                         matchingResult, suffixExpSize, suffixActSize, iAct);

      iExp++;
      iAct += suffixActSize - suffixExpSize;
    }

    return allMatched && matchRestActualTypesWithEmptyExpectedTypes(actualElementTypes, exactMatch, reversed, matchingResult, iAct,
                                                                    actualSize);
  }

  private static boolean matchGenericVariadicWithElementTypes(@NotNull PyGenericVariadicType genericVariadicType,
                                                              @NotNull List<PyType> elementTypes,
                                                              @NotNull MatchContext context, boolean reversed,
                                                              @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult) {
    if (genericVariadicType.isHomogeneous()) {
      PyType iteratedType = genericVariadicType.getIteratedItemType();
      boolean elementsMatched = true;
      for (var elementType: elementTypes) {
        boolean matched;
        if (elementType instanceof PyGenericVariadicType) {
          matched = reversed ?
                    match(elementType, genericVariadicType, context).orElse(false) :
                    match(genericVariadicType, elementType, context);
        }
        else {
          matched = reversed ?
                    match(elementType, iteratedType, context).orElse(false) :
                    match(iteratedType, elementType, context).orElse(false);
        }
        elementsMatched &= matched;
        if (!reversed) {
          matchingResult.add(new Trinity<>(elementType, iteratedType, matched));
        }
      }
      if (reversed) {
        matchingResult.add(new Trinity<>(genericVariadicType,
                                         genericVariadicType.withElementTypes(false, elementTypes), elementsMatched));
      }
      return elementsMatched;
    }
    else {
      var expectedGenericVariadicMatch = genericVariadicType.withElementTypes(false, elementTypes);
      context.mySubstitutions.typeVarTuples
        .put(genericVariadicType, expectedGenericVariadicMatch);
      if (!reversed) {
        matchingResult.addAll(ContainerUtil.map(elementTypes, it -> new Trinity<>(it, genericVariadicType, true)));
      }
      else {
        matchingResult.add(new Trinity<>(genericVariadicType, expectedGenericVariadicMatch, true));
      }
      return true;
    }
  }

  // Expected: Ts*, T1, T2, ... Tn
  // Actual:   V1, V2, ... Vm,  Ts1*
  private static boolean matchMiddlePartElementTypesBothGenericVariadic(@NotNull List<PyType> expectedElementTypes,
                                                                        @NotNull List<PyType> actualElementTypes,
                                                                        @NotNull MatchContext context, boolean reversed,
                                                                        @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult) {
    if (expectedElementTypes.size() == 1) {
      return matchGenericVariadicWithElementTypes((PyGenericVariadicType)expectedElementTypes.get(0), actualElementTypes, context,
                                                  reversed, matchingResult);
    }
    if (actualElementTypes.size() == 1) {
      return matchGenericVariadicWithElementTypes((PyGenericVariadicType)actualElementTypes.get(0), expectedElementTypes, context,
                                                  !reversed, matchingResult);
    }

    var expectedGenericVariadic = (PyGenericVariadicType)expectedElementTypes.get(0);
    var actualGenericVariadic = (PyGenericVariadicType)actualElementTypes.get(actualElementTypes.size() - 1);

    // In case of Ts* and Ts1* are both not homogeneous its possible to match
    // Ts* -> (V1, V2, ... Vm, Any, Any ...)
    // Ts1* -> (Any, Any, ..., T1, T2, ..., Tn)
    if (!expectedGenericVariadic.isHomogeneous() && !actualGenericVariadic.isHomogeneous()) {
      var expectedMatch = new ArrayList<>(actualElementTypes.subList(0, actualElementTypes.size() - 1));
      expectedMatch.add(PyGenericVariadicType.homogeneous(null));
      var expectedGenericVariadicMatch = actualGenericVariadic.withElementTypes(false, expectedMatch);
      context.mySubstitutions.typeVarTuples.put(expectedGenericVariadic, expectedGenericVariadicMatch);
      var actualMatch = new ArrayList<PyType>();
      actualMatch.add(PyGenericVariadicType.homogeneous(null));
      actualMatch.addAll(expectedElementTypes.subList(1, expectedElementTypes.size()));
      var actualGenericVariadicMatch = actualGenericVariadic.withElementTypes(false, actualMatch);
      context.mySubstitutions.typeVarTuples.put(actualGenericVariadic, actualGenericVariadicMatch);
      if (!reversed) {
        matchingResult.addAll(ContainerUtil.map(expectedMatch, it -> new Trinity<>(it, expectedGenericVariadic, true)));
        matchingResult.add(new Trinity<>(actualGenericVariadic, actualGenericVariadicMatch, true));
      }
      else {
        matchingResult.add(new Trinity<>(expectedGenericVariadic, expectedGenericVariadicMatch, true));
        matchingResult.addAll(ContainerUtil.map(actualMatch, it -> new Trinity<>(it, actualGenericVariadic, true)));
      }
      return true;
    }

    // In case of Ts* or Ts1* is homogeneous we need to brute force prefix of actual elements and find first match
    // Try match:
    // Ts* -> (V1, V2, ... V[elementSz-1])
    // (T1, T2, ..., Tn) -> (V[elementTz], V[elementSz+1], ..., Vm, Ts1*) - is case with only one generic variadic generic
    // Also include the cases of split Ts1* if Ts1* is homogeneous:
    // Ts* -> (V1, ..., Vn, Ts1*)
    // (T1, ..., Tn) -> Ts1*
    int start = reversed ? 0 : actualElementTypes.size();
    int step = reversed ? 1 : -1;
    int end = reversed ? -1 : actualElementTypes.size() + 1;
    var curContext = context.copy();
    var curMatchingResult = new ArrayList<>(matchingResult);
    var expectedSuffix = expectedElementTypes.subList(1, expectedElementTypes.size());
    for (int elementsSz = start; elementsSz != end; elementsSz += step) {
      curContext = context.copy();
      curMatchingResult = new ArrayList<>(matchingResult);

      var actElements = actualElementTypes.subList(0, elementsSz);
      boolean allMatched = matchGenericVariadicWithElementTypes(expectedGenericVariadic, actElements, curContext, reversed, curMatchingResult);

      // last element can be split into 2 parts so Math.min to avoid empty
      var actualSuffix = actualElementTypes.subList(Math.min(elementsSz, actualElementTypes.size() - 1), actualElementTypes.size());
      allMatched &= matchElementTypes(expectedSuffix, actualSuffix, curContext, true, reversed, false, curMatchingResult);
      if (!allMatched) continue;

      matchingResult.clear();
      matchingResult.addAll(curMatchingResult);
      context.mySubstitutions.putAll(curContext.mySubstitutions);
      return true;
    }

    matchingResult.clear();
    matchingResult.addAll(curMatchingResult);
    return false;
  }

  private static Optional<Boolean> tryMatchElementTypesBothGenericVariadic(@NotNull List<PyType> expectedElementTypes,
                                                                           @NotNull List<PyType> actualElementTypes,
                                                                           @NotNull MatchContext context, boolean exactMatch,
                                                                           boolean reversed, boolean collectEqualsTypeVars,
                                                                           @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult) {
    expectedElementTypes = substituteGenericVariadicInElementTypes(expectedElementTypes, context);
    actualElementTypes = substituteGenericVariadicInElementTypes(actualElementTypes, context);

    var expectedGenericVariadics = getVariadicGenerics(expectedElementTypes);
    var actualGenericVariadics = getVariadicGenerics(actualElementTypes);

    if (!actualGenericVariadics.isEmpty()) {
      if (!expectedGenericVariadics.isEmpty()) {
        if (expectedElementTypes.size() == 1 && actualElementTypes.size() == 1) {
          var expected = (PyGenericVariadicType)expectedElementTypes.get(0);
          var actual = (PyGenericVariadicType)actualElementTypes.get(0);
          return Optional.of(match(expected, actual, context));
        }
        return Optional.of(matchElementTypesBothGenericVariadic(expectedElementTypes, actualElementTypes, context, reversed, matchingResult));
      }
      return Optional.of(matchElementTypes(actualElementTypes, expectedElementTypes, context, exactMatch, !reversed, collectEqualsTypeVars,
                                           matchingResult));
    }

    return Optional.empty();
  }

  // Here we want to simplify all cases to the case:
  // ..., Ts*, T1, T2, ... Tn, ...
  // ..., V1, V2, ..., Vn, Ts1*, ...
  // Use reverse for expected and actual in the case of Ts1* goes earlier than Ts*
  // Prefix size determined by the position of Ts* and the suffix by the position of Ts1*
  private static boolean matchElementTypesBothGenericVariadic(@NotNull List<PyType> expectedElementTypes, @NotNull List<PyType> actualElementTypes,
                                                              @NotNull MatchContext context, boolean reversed,
                                                              @NotNull List<Trinity<PyType, PyType, Boolean>> matchingResult) {
    int indExpGenericVariadic = -1;
    int indActGenericVariadic = -1;
    for (int i = 0; i < Math.max(expectedElementTypes.size(), actualElementTypes.size()); ++i) {
      if (i < expectedElementTypes.size() && expectedElementTypes.get(i) instanceof PyGenericVariadicType) {
        indExpGenericVariadic = i;
      }
      if (i < actualElementTypes.size() && actualElementTypes.get(i) instanceof PyGenericVariadicType) {
        if (indExpGenericVariadic == -1) {
          return matchElementTypesBothGenericVariadic(actualElementTypes, expectedElementTypes, context, !reversed, matchingResult);
        }
        else {
          indActGenericVariadic = i;
          break;
        }
      }
    }
    assert indExpGenericVariadic >= 0 && indActGenericVariadic >= 0;

    boolean allMatched = true;

    int prefixLen = indExpGenericVariadic;
    int suffixLen = Math.min(expectedElementTypes.size() - indExpGenericVariadic - 1, actualElementTypes.size() - indActGenericVariadic - 1);
    if (prefixLen > 0) {
      var expectedPrefix = expectedElementTypes.subList(0, prefixLen);
      var actualPrefix = actualElementTypes.subList(0, prefixLen);
      allMatched = matchElementTypes(expectedPrefix, actualPrefix, context, true, reversed, false, matchingResult);
    }

    var expectedMiddlePart = expectedElementTypes.subList(prefixLen, expectedElementTypes.size() - suffixLen);
    var actualMiddlePart = actualElementTypes.subList(prefixLen, actualElementTypes.size() - suffixLen);
    allMatched &= matchMiddlePartElementTypesBothGenericVariadic(expectedMiddlePart, actualMiddlePart, context, reversed, matchingResult);

    if (suffixLen > 0) {
      var expectedSuffix = expectedElementTypes.subList(expectedElementTypes.size() - suffixLen, expectedElementTypes.size());
      var actualSuffix = actualElementTypes.subList(actualElementTypes.size() - suffixLen, actualElementTypes.size());
      allMatched &= matchElementTypes(expectedSuffix, actualSuffix, context, true, reversed, false, matchingResult);
    }

    return allMatched;
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyTupleType expected, @NotNull PyTupleType actual, @NotNull MatchContext context) {
    if (!expected.isHomogeneous() && !actual.isHomogeneous()) {
      return Optional.of(matchElementTypes(expected.getElementTypes(), actual.getElementTypes(), context, true, false));
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

  @NotNull
  public static MatchContext getMatchContext(@NotNull TypeEvalContext context, @NotNull GenericSubstitutions substitutions) {
    return new MatchContext(context, substitutions, false);
  }

  public static boolean matchCallableParameters(@NotNull List<PyCallableParameter> expectedParameters,
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

    List<PyType> expectedElementTypes = new ArrayList<>();
    List<PyType> actualElementTypes = new ArrayList<>();

    final int size = Math.max(expectedParameters.size(), actualParameters.size());
    for (int i = startIndex; i < size; i++) {
      final var expectedParam = i < expectedParameters.size() ? expectedParameters.get(i) : null;
      final var actualParam = i < actualParameters.size() ? actualParameters.get(i) : null;
      if (actualParam != null) {
        boolean couldBeMapped = expectedParam == null || couldBeMappedOntoPositionalContainer(expectedParam);
        final PyType actualParamType =
          actualParam.isPositionalContainer() && couldBeMapped
          ? actualParam.getArgumentType(context)
          : actualParam.getType(context);
        actualElementTypes.add(actualParamType);
      }
      if (expectedParam != null) {
        expectedElementTypes.add(expectedParam.getType(context));
      }
    }

    // actual callable type could accept more general parameter type
    return matchElementTypes(actualElementTypes, expectedElementTypes, matchContext.reverseSubstitutions(), false, false);
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

  private static boolean couldBeMappedOntoPositionalContainer(@NotNull PyCallableParameter parameter) {
    if (parameter.isPositionalContainer() || parameter.isKeywordContainer()) return false;

    final var psi = parameter.getParameter();
    if (psi != null) {
      final var namedPsi = psi.getAsNamed();
      if (namedPsi != null && namedPsi.isKeywordOnly()) {
        return false;
      }
    }

    return true;
  }

  private static boolean consistsOfSameElementNumberTuples(@NotNull PyUnionType unionType, int elementCount) {
    for (PyType type : unionType.getMembers()) {
      if (type instanceof PyTupleType tupleType) {

        if (!tupleType.isHomogeneous() && elementCount != tupleType.getElementCount()) {
          return false;
        }
      }
      else {
        return false;
      }
    }

    return true;
  }

  private static boolean substituteExpectedElementsWithUnions(@NotNull PyTupleType expected,
                                                              int elementCount,
                                                              @NotNull PyUnionType actual,
                                                              @NotNull MatchContext context) {
    for (int i = 0; i < elementCount; i++) {
      final int currentIndex = i;

      final PyType elementType = PyUnionType.union(
        StreamEx
          .of(actual.getMembers())
          .select(PyTupleType.class)
          .map(type -> type.getElementType(currentIndex))
          .toList()
      );

      if (!match(expected.getElementType(i), elementType, context).orElse(true)) {
        return false;
      }
    }

    return true;
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
        if (entry.getKey() instanceof PyGenericVariadicType) {
          if (entry.getValue() instanceof final PyGenericVariadicType gvt) {
            result.typeVarTuples.put((PyGenericVariadicType)entry.getKey(), gvt);
          }
          else {
            result.typeVarTuples.put((PyGenericVariadicType)entry.getKey(), null);
          }
        }
        else if (entry.getKey() instanceof PyGenericType) {
          result.typeVars.put((PyGenericType)entry.getKey(), entry.getValue());
        }
      }
      if (!classType.isDefinition()) {
        PyCollectionType genericDefinitionType = as(provider.getGenericType(classType.getPyClass(), context), PyCollectionType.class);
        if (genericDefinitionType != null) {
          List<PyType> definitionTypeParameters = genericDefinitionType.getElementTypes();
          List<PyType> instanceTypeArguments =
            classType instanceof PyCollectionType ? ((PyCollectionType)classType).getElementTypes() : List.of();

          if (ContainerUtil.exists(definitionTypeParameters, it -> it instanceof PyGenericVariadicType)) {
            var matchContext = getMatchContext(context, result);
            matchElementTypes(definitionTypeParameters, instanceTypeArguments, matchContext, true, true);
          }
          else {
            for (int i = 0; i < definitionTypeParameters.size(); i++) {
              PyType typeParameter = definitionTypeParameters.get(i);
              PyType typeArgument = ContainerUtil.getOrElse(instanceTypeArguments, i, null);
              if (typeParameter instanceof PyGenericType) {
                result.typeVars.put((PyGenericType)typeParameter, typeArgument);
              }
              else if (typeParameter instanceof PyParamSpecType) {
                result.getParamSpecs().put((PyParamSpecType)typeParameter, as(typeArgument, PyParamSpecType.class));
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
    if (actualElementTypes.isEmpty()) {
      for (PyType type : expectedElementTypes) {
        if (type instanceof PyGenericVariadicType) continue;
        if (!match(type, (@Nullable PyType)null, context).orElse(true)) {
          return false;
        }
      }
      return true;
    }

    return matchElementTypes(expectedElementTypes, actualElementTypes, context, checkExactMatch(expected, actual), false);
  }

  private static boolean isCollectionOfLiterals(@Nullable PyType type) {
    if (type == null) return true;
    if (type instanceof PyLiteralType) return true;
    if (type instanceof final PyUnionType unionType) {
      return ContainerUtil.all(unionType.getMembers(), it -> isCollectionOfLiterals(it));
    }
    if (type instanceof final PyCollectionType collectionType) {
      return ContainerUtil.all(collectionType.getElementTypes(), it -> isCollectionOfLiterals(it));
    }
    return false;
  }

  private static boolean checkExactMatch(@NotNull PyCollectionType expected, @NotNull PyType actual) {
    if (actual instanceof final PyClassLikeType actualClassLikeType) {
      String expectedQName = expected.getClassQName();
      String actualQName = actualClassLikeType.getClassQName();
      if ("typing.ValuesView".equals(expectedQName) && "_dict_values".equals(actualQName)) return false;
      if ("typing.Awaitable".equals(expectedQName) && "typing.Coroutine".equals(actualQName)) return false;
      if (("typing.Iterable".equals(expectedQName) || "typing.Iterator".equals(expectedQName) ||
           "typing.Container".equals(expectedQName)) &&
          "typing.Generator".equals(actualQName)) return false;
      if ("typing.AsyncIterator".equals(expectedQName) && "typing.AsyncGenerator".equals(actualQName)) return false;
      if (isCollectionOfLiterals(expected) && isCollectionOfLiterals(actual)) return false;
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
  public static GenericSubstitutions
  getSubstitutionsWithUnresolvedReturnGenerics(@NotNull Collection<PyCallableParameter> parameters,
                                               @Nullable PyType returnType,
                                               @Nullable GenericSubstitutions substitutions,
                                               @NotNull TypeEvalContext context) {
    final var result = substitutions == null ? new GenericSubstitutions() : substitutions;
    final var visited = new HashSet<>();
    final var returnTypeGenerics = new Generics();
    collectGenerics(returnType, context, returnTypeGenerics, visited);
    if (returnTypeGenerics.typeVars.isEmpty()) {
      return result;
    }
    for (PyGenericType alreadyKnown : result.typeVars.keySet()) {
      if (!returnTypeGenerics.typeVars.remove(alreadyKnown)) {
        returnTypeGenerics.typeVars.remove(invert(alreadyKnown));
      }
    }
    if (returnTypeGenerics.isEmpty()) {
      return result;
    }

    visited.clear();
    final var paramGenerics = new Generics();
    for (PyCallableParameter parameter : parameters) {
      collectGenerics(parameter.getArgumentType(context), context, paramGenerics, visited);
    }

    for (PyGenericType returnTypeGeneric : returnTypeGenerics.typeVars) {
      if (!paramGenerics.typeVars.contains(returnTypeGeneric) && !paramGenerics.typeVars.contains(invert(returnTypeGeneric))) {
        result.typeVars.put(returnTypeGeneric, returnTypeGeneric);
      }
    }
    return result;
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
    if (type instanceof PyGenericType) {
      generics.allTypeVarsAndTypeVarTuples.add((PyGenericType)type);
    }
    if (visited.contains(type)) {
      return;
    }
    visited.add(type);
    if (type instanceof PyGenericType) {
      if (type instanceof PyGenericVariadicType) {
        generics.typeVarTuples.add((PyGenericVariadicType)type);
      }
      else {
        generics.typeVars.add((PyGenericType)type);
      }
    }
    if (type instanceof PyParamSpecType) {
      generics.paramSpecs.add((PyParamSpecType)type);
    }
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
  }

  @NotNull
  public static List<@Nullable PyType> substituteExpand(@Nullable PyType type,
                                                        @NotNull GenericSubstitutions substitutions,
                                                        @NotNull TypeEvalContext context,
                                                        @NotNull Set<PyType> substituting) {
    var result = new ArrayList<PyType>();
    if (type instanceof final PyGenericVariadicType genericVariadicType) {
      var elementTypes = genericVariadicType.getMappedElementTypes(substitutions.typeVarTuples);
      if (elementTypes == null) {
        if (genericVariadicType.isHomogeneous()) {
          PyType homoType = genericVariadicType.getIteratedItemType();
          PyType substHomoType = substitute(homoType, substitutions, context, substituting);
          if (homoType == substHomoType) {
            return List.of(type);
          }
          else {
            return List.of(PyGenericVariadicType.homogeneous(substHomoType));
          }
        }
        return List.of(type);
      }

      return ContainerUtil.flatMap(elementTypes, it -> substituteExpand(it, substitutions, context, substituting));
    }
    result.add(substitute(type, substitutions, context, substituting));
    return result;
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
        if (type instanceof final PyGenericVariadicType genericVariadicType) {
          var expandedElementTypes = substituteExpand(type, substitutions, context, substituting);
          if (expandedElementTypes.size() == 1) {
            return expandedElementTypes.get(0);
          }
          return genericVariadicType.withElementTypes(false, expandedElementTypes);
        }
        if (type instanceof final PyGenericType typeVar) {
          PyType substitution = substitutions.typeVars.get(typeVar);
          if (substitution == null) {
            final PyInstantiableType<?> invertedTypeVar = invert(typeVar);
            final PyInstantiableType<?> invertedSubstitution = as(substitutions.typeVars.get(invertedTypeVar), PyInstantiableType.class);
            if (invertedSubstitution != null) {
              substitution = invert(invertedSubstitution);
            }
          }
          // TODO remove !typeVar.equals(substitution) part, it's necessary due to the logic in unifyReceiverWithParamSpecs
          if (substitution instanceof PyGenericType) {
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
            if (!typeVar.equals(substitution) && substitutions.typeVars.containsKey(substitution)) {
              return substitute(substitution, substitutions, context, substituting);
            }
          }
          else if (hasGenerics(substitution, context)) {
            return substitute(substitution, substitutions, context, substituting);
          }
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
          final List<PyType> elementTypes = collection.getElementTypes();
          final List<PyType> substitutes = new ArrayList<>();
          for (PyType elementType : elementTypes) {
            if (elementType instanceof final PyParamSpecType paramSpecType) {
              final var paramSpecTypeSubst = substitutions.paramSpecs.get(paramSpecType);
              if (paramSpecTypeSubst != null && paramSpecTypeSubst.getParameters() != null) {
                substitutes.add(paramSpecTypeSubst);
              }
            }
            else {
              substitutes.addAll(substituteExpand(elementType, substitutions, context, substituting));
            }
          }
          return new PyCollectionTypeImpl(collection.getPyClass(), collection.isDefinition(), substitutes);
        }
        else if (type instanceof PyTupleType) {
          final PyTupleType tupleType = (PyTupleType)type;
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
          List<PyCallableParameter> substParams = null;
          final List<PyCallableParameter> parameters = callable.getParameters(context);
          if (parameters != null) {
            substParams = new ArrayList<>();
            for (PyCallableParameter parameter : parameters) {
              final var parameterType = parameter.getType(context);
              if (parameters.size() == 1 && parameterType instanceof PyParamSpecType) {
                final var parameterTypeSubst = substitutions.paramSpecs.get(parameterType);
                if (parameterTypeSubst != null && parameterTypeSubst.getParameters() != null) {
                  substParams = parameterTypeSubst.getParameters();
                  break;
                }
              }
              if (parameters.size() == 1 && parameterType instanceof PyConcatenateType concatenateType) {
                final var paramSpecType = concatenateType.getParamSpec();
                final var paramSpecTypeSubst = substitutions.paramSpecs.get(paramSpecType);
                if (paramSpecTypeSubst != null && paramSpecTypeSubst.getParameters() != null) {
                  final var firstParameters = ContainerUtil
                    .map(concatenateType.getFirstTypes(), it -> PyCallableParameterImpl.nonPsi(it));
                  substParams.addAll(firstParameters);
                  substParams.addAll(paramSpecTypeSubst.getParameters());
                  break;
                }
              }

              final List<PyType> substTypes = substituteExpand(parameter.getType(context), substitutions, context, substituting);
              final PyParameter psi = parameter.getParameter();
              final List<PyCallableParameter> substs =
                psi != null ?
                ContainerUtil.map(substTypes, it -> PyCallableParameterImpl.psi(psi, it)) :
                ContainerUtil.map(substTypes, it -> PyCallableParameterImpl.nonPsi(parameter.getName(), it, parameter.getDefaultValue()));

              substParams.addAll(substs);
            }
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
    final var substitutions = unifyReceiver(receiver, context);
    for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(arguments).entrySet()) {
      final PyCallableParameter paramWrapper = entry.getValue();
      final PyType expectedType = paramWrapper.getArgumentType(context);
      final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(entry.getKey(), expectedType, context, substitutions);
      var actualType = promotedToLiteral != null ? promotedToLiteral : context.getType(entry.getKey());
      if (paramWrapper.isSelf()) {
        // TODO find out a better way to pass the corresponding function inside
        final PyParameter param = paramWrapper.getParameter();
        final PyFunction function = as(ScopeUtil.getScopeOwner(param), PyFunction.class);
        assert function != null;
        if (function.getModifier() == PyFunction.Modifier.CLASSMETHOD) {
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
        PyCollectionType genericClass = findGenericDefinitionType(containingClass, context);
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
    if (expectedArgumentType instanceof final PyGenericVariadicType genericVariadicType) {
      var elementTypes = genericVariadicType.getMappedElementTypes(substitutions.typeVarTuples);
      if (elementTypes != null) {
        return matchElementTypes(elementTypes, actualArgumentTypes, getMatchContext(context, substitutions), true, false);
      }
      else {
        substitutions.typeVarTuples.put(genericVariadicType,
                                        ((PyGenericVariadicType)expectedArgumentType).withElementTypes(false, actualArgumentTypes));
        return true;
      }
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
          for (Map.Entry<PyGenericType, PyType> typeVarMapping : newSubstitutions.typeVars.entrySet()) {
            substitutions.typeVars.putIfAbsent(typeVarMapping.getKey(), typeVarMapping.getValue());
          }
          for (Map.Entry<PyGenericVariadicType, PyGenericVariadicType> typeVarMapping : newSubstitutions.typeVarTuples.entrySet()) {
            substitutions.typeVarTuples.putIfAbsent(typeVarMapping.getKey(), typeVarMapping.getValue());
          }
          for (Map.Entry<PyParamSpecType, PyParamSpecType> paramSpecMapping : newSubstitutions.paramSpecs.entrySet()) {
            substitutions.paramSpecs.putIfAbsent(paramSpecMapping.getKey(), paramSpecMapping.getValue());
          }
        });
    }
    return substitutions;
  }

  private static void replaceUnresolvedGenericsWithAny(@NotNull Map<PyGenericType, PyType> substitutions) {
    final List<PyType> unresolvedGenerics =
      ContainerUtil.filter(substitutions.values(), type -> type instanceof PyGenericType && !substitutions.containsKey(type));

    for (PyType unresolvedGeneric : unresolvedGenerics) {
      substitutions.put((PyGenericType)unresolvedGeneric, null);
    }
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
    if (type instanceof PyGenericType) {
      if (((PyGenericType)type).isDefinition()) {
        return true;
      }

      return isCallable(((PyGenericType)type).getBound());
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
  public static PyType getTargetTypeFromTupleAssignment(@NotNull PyTargetExpression target,
                                                        @NotNull PyTupleExpression parentTuple,
                                                        @NotNull PyType assignedType,
                                                        @NotNull TypeEvalContext context) {
    if (assignedType instanceof PyTupleType) {
      return getTargetTypeFromTupleAssignment(target, parentTuple, (PyTupleType)assignedType);
    }
    else if (assignedType instanceof PyClassLikeType) {
      return StreamEx
        .of(((PyClassLikeType)assignedType).getAncestorTypes(context))
        .select(PyNamedTupleType.class)
        .findFirst()
        .map(t -> getTargetTypeFromTupleAssignment(target, parentTuple, t))
        .orElse(null);
    }

    return null;
  }

  @Nullable
  public static PyType getTargetTypeFromTupleAssignment(@NotNull PyTargetExpression target, @NotNull PyTupleExpression parentTuple,
                                                        @NotNull PyTupleType assignedTupleType) {
    final int count = assignedTupleType.getElementCount();
    final PyExpression[] elements = parentTuple.getElements();
    if (elements.length == count || assignedTupleType.isHomogeneous()) {
      final int index = ArrayUtil.indexOf(elements, target);
      if (index >= 0) {
        return assignedTupleType.getElementType(index);
      }
      for (int i = 0; i < count; i++) {
        PyExpression element = elements[i];
        while (element instanceof PyParenthesizedExpression) {
          element = ((PyParenthesizedExpression)element).getContainedExpression();
        }
        if (element instanceof PyTupleExpression) {
          final PyType elementType = assignedTupleType.getElementType(i);
          if (elementType instanceof PyTupleType) {
            final PyType result = getTargetTypeFromTupleAssignment(target, (PyTupleExpression)element, (PyTupleType)elementType);
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
      var matchContext = getMatchContext(context, substitutions);
      List<PyType> generics = new ArrayList<>();
      generics.addAll(typeParams.typeVars);
      generics.addAll(typeParams.typeVarTuples);
      matchElementTypes(generics, actualTypeParams, matchContext, true, true);
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
    private final Set<PyGenericType> typeVars = new LinkedHashSet<>();

    @NotNull
    private final Set<PyGenericVariadicType> typeVarTuples = new LinkedHashSet<>();

    @NotNull
    private final List<PyGenericType> allTypeVarsAndTypeVarTuples = new ArrayList<>();

    @NotNull
    private final Set<PyParamSpecType> paramSpecs = new LinkedHashSet<>();

    @NotNull
    private final Set<PyConcatenateType> concatenates = new LinkedHashSet<>();

    @Nullable
    private PySelfType self;

    //Generics() {
    //  this(new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), null);
    //}
    //
    //Generics(@NotNull Set<PyGenericType> generics,
    //         @NotNull Set<PyGenericVariadicType> genericVariadics,
    //         @NotNull List<PyGenericType> typeVarsAndTuples,
    //         @NotNull Set<PyParamSpecType> paramSpecs,
    //         @NotNull Set<PyConcatenateType> concatenates,
    //         @Nullable PySelfType self) {
    //  this.typeVars = generics;
    //  this.typeVarTuples = genericVariadics;
    //  this.typeVarsAndTuples = typeVarsAndTuples;
    //  this.paramSpecs = paramSpecs;
    //  this.concatenates = concatenates;
    //  this.self = self;
    //}

    public @NotNull Set<PyGenericType> getTypeVars() {
      return Collections.unmodifiableSet(typeVars);
    }

    public @NotNull Set<PyGenericVariadicType> getTypeVarTuples() {
      return Collections.unmodifiableSet(typeVarTuples);
    }

    public @NotNull List<PyGenericType> getAllTypeVarsAndTypeVarTuples() {
      return Collections.unmodifiableList(allTypeVarsAndTypeVarTuples);
    }

    public @NotNull List<PyGenericType> getTypeVarsAndTuples() {
      var result = new ArrayList<PyGenericType>();
      result.addAll(typeVars);
      result.addAll(typeVarTuples);
      return result;
    }

    public @NotNull Set<PyParamSpecType> getParamSpecs() {
      return Collections.unmodifiableSet(paramSpecs);
    }

    public boolean isEmpty() {
      return typeVars.isEmpty() && typeVarTuples.isEmpty() && paramSpecs.isEmpty() && concatenates.isEmpty() && self == null;
    }
  }

  @ApiStatus.Experimental
  public static class GenericSubstitutions {
    @NotNull
    private final Map<PyGenericType, PyType> typeVars;

    @NotNull
    private final Map<PyGenericVariadicType, PyGenericVariadicType> typeVarTuples;

    @NotNull
    private final Map<PyParamSpecType, PyParamSpecType> paramSpecs;

    @Nullable
    private PyType qualifierType;

    public GenericSubstitutions(@NotNull Map<PyGenericType, PyType> typeVars) {
      this(typeVars, new LinkedHashMap<>(), new LinkedHashMap<>(), null);
    }

    public GenericSubstitutions() {
      this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), null);
    }

    GenericSubstitutions(@NotNull Map<PyGenericType, PyType> typeVars,
                         @NotNull Map<PyGenericVariadicType, PyGenericVariadicType> typeVarTuples,
                         @NotNull Map<PyParamSpecType, PyParamSpecType> paramSpecs,
                         @Nullable PyType qualifierType) {
      this.typeVars = typeVars;
      this.typeVarTuples = typeVarTuples;
      this.paramSpecs = paramSpecs;
      this.qualifierType = qualifierType;
    }

    @NotNull
    public Map<PyParamSpecType, PyParamSpecType> getParamSpecs() {
      return paramSpecs;
    }

    public Map<PyGenericType, PyType> getTypeVars() {
      return typeVars;
    }

    public Map<PyGenericVariadicType, PyGenericVariadicType> getTypeVarTuples() {
      return typeVarTuples;
    }

    @Nullable
    public PyType getQualifierType() {
      return qualifierType;
    }

    @NotNull
    public GenericSubstitutions copy() {
      return new GenericSubstitutions(new LinkedHashMap<>(typeVars), new LinkedHashMap<>(typeVarTuples), new LinkedHashMap<>(paramSpecs),
                                      qualifierType);
    }

    public void putAll(@NotNull GenericSubstitutions substitutions) {
      typeVars.putAll(substitutions.typeVars);
      typeVarTuples.putAll(substitutions.typeVarTuples);
      paramSpecs.putAll(substitutions.paramSpecs);
      qualifierType = substitutions.qualifierType;
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

    @NotNull MatchContext copy() {
      return new MatchContext(context, mySubstitutions.copy(), reversedSubstitutions);
    }
  }
}
