// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
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
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyUtil.getReturnTypeToAnalyzeAsCallType;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;

/**
 * @author vlan
 */
public final class PyTypeChecker {
  private PyTypeChecker() {
  }

  /**
   * See {@link PyTypeChecker#match(PyType, PyType, TypeEvalContext, Map)} for description.
   */
  public static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    return match(expected, actual, new MatchContext(context, new HashMap<>())).orElse(true);
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
                              @NotNull Map<PyGenericType, PyType> substitutions) {
    return match(expected, actual, new MatchContext(context, substitutions)).orElse(true);
  }

  public static boolean match(@Nullable PyType expected,
                              @Nullable PyType actual,
                              @NotNull TypeEvalContext context,
                              @NotNull GenericSubstitutions substitutions) {
    return match(expected, actual, new MatchContext(context, substitutions.typeVars, substitutions.paramSpecs, false))
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
    for (PyTypeCheckerExtension extension : PyTypeCheckerExtension.EP_NAME.getExtensionList()) {
      final Optional<Boolean> result = extension.match(expected, actual, context.context, context.genericSubstitutions);
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

    if (actual instanceof PyGenericType && context.reversedSubstitutions) {
      return Optional.of(match((PyGenericType)actual, expected, context));
    }

    if (expected instanceof PyGenericType) {
      return Optional.of(match((PyGenericType)expected, actual, context));
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

    if (actual instanceof PyCallableType && expected instanceof PyCallableType) {
      final PyCallableType expectedCallable = (PyCallableType)expected;
      final PyCallableType actualCallable = (PyCallableType)actual;
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

    final PyType substitution = context.genericSubstitutions.get(expected);
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
      context.genericSubstitutions.put(expected, actual);
    }
    else if (bound != null) {
      context.genericSubstitutions.put(expected, PyUnionType.createWeakType(bound));
    }

    return true;
  }

  private static boolean match(@NotNull PyParamSpecType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    if (actual == null) return true;
    if (!(actual instanceof PyParamSpecType)) return false;
    final var callableActual = (PyParamSpecType)actual;
    final var parameters = callableActual.getParameters();
    if (parameters == null) return false;
    context.paramSpecSubstitutions.put(expected, expected.withParameters(parameters, context.context));
    return true;
  }

  private static boolean match(@NotNull PyType expected, @NotNull PyUnionType actual, @NotNull MatchContext context) {
    if (expected instanceof PyTupleType) {
      Optional<Boolean> match = match((PyTupleType)expected, actual, context);
      if (match.isPresent()) {
        return match.get();
      }
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

    final PyClass superClass = expected.getPyClass();
    final PyClass subClass = actual.getPyClass();
    final boolean matchClasses = matchClasses(superClass, subClass, context);

    if (PyProtocolsKt.isProtocol(expected, context) && !matchClasses) {
      if (expected instanceof PyCollectionType && !matchGenerics((PyCollectionType)expected, actual, matchContext)) {
        return Optional.of(false);
      }

      // methods from the actual will be matched against method definitions from the expected below
      // here we make substitutions from expected definition to its usage
      StreamEx
        .of(PyTypeProvider.EP_NAME.getExtensionList())
        .map(provider -> provider.getGenericType(superClass, context))
        .select(PyCollectionType.class)
        .findFirst()
        .ifPresent(it -> matchGenerics(it, expected, matchContext));

      for (kotlin.Pair<PyTypedElement, List<RatedResolveResult>> pair : PyProtocolsKt.inspectProtocolSubclass(expected, actual, context)) {
        final List<RatedResolveResult> subclassElements = pair.getSecond();
        if (ContainerUtil.isEmpty(subclassElements)) {
          return Optional.of(false);
        }

        final PyType protocolElementType = dropSelfIfNeeded(expected, context.getType(pair.getFirst()), context);

        final boolean elementResult = StreamEx
          .of(subclassElements)
          .map(ResolveResult::getElement)
          .select(PyTypedElement.class)
          .map(context::getType)
          .anyMatch(
            subclassElementType -> {
              return match(protocolElementType,
                           dropSelfIfNeeded(actual, subclassElementType, context), matchContext).orElse(true);
            }
          );

        if (!elementResult) {
          return Optional.of(false);
        }
      }

      return Optional.of(true);
    }

    if (expected instanceof PyCollectionType) {
      return Optional.of(match((PyCollectionType)expected, actual, matchContext));
    }

    if (matchClasses) {
      if (expected instanceof PyTypingNewType && !expected.equals(actual) && superClass.equals(subClass)) {
        return Optional.of(actual.getAncestorTypes(context).contains(expected));
      }
      return Optional.of(true);
    }

    if (expected.equals(actual)) {
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private static @Nullable PyType dropSelfIfNeeded(@NotNull PyClassType classType,
                                                   @Nullable PyType elementType,
                                                   @NotNull TypeEvalContext context) {
    if (elementType instanceof PyFunctionType) {
      PyFunctionType functionType = (PyFunctionType)elementType;
      if (PyUtil.isInitOrNewMethod(functionType.getCallable()) || !classType.isDefinition()) {
        return functionType.dropSelf(context);
      }
    }
    return elementType;
  }


  @NotNull
  private static Optional<Boolean> match(@NotNull PyTupleType expected, @NotNull PyTupleType actual, @NotNull MatchContext context) {
    if (!expected.isHomogeneous() && !actual.isHomogeneous()) {
      if (expected.getElementCount() != actual.getElementCount()) {
        return Optional.of(false);
      }

      for (int i = 0; i < expected.getElementCount(); i++) {
        if (!match(expected.getElementType(i), actual.getElementType(i), context).orElse(true)) {
          return Optional.of(false);
        }
      }
      return Optional.of(true);
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
        final int size = Math.min(expectedParameters.size(), actualParameters.size());
        for (int i = 0; i < size; i++) {
          final var expectedParam = expectedParameters.get(i);
          final var actualParam = actualParameters.get(i);
          final var expectedParamType = expectedParam.getType(context);
          // TODO: Check named and star params, not only positional ones
          if (expectedParamType instanceof PyParamSpecType && expectedParameters.size() == 1) {
            final var expectedParamSpecType = (PyParamSpecType)expectedParamType;
            matchContext.paramSpecSubstitutions.put(expectedParamSpecType, expectedParamSpecType.withParameters(actualParameters, context));
            break;
          }
          else if (expectedParamType instanceof PyConcatenateType && expectedParameters.size() == 1) {
            if (i != 0) break;

            final var actualParamType = actualParam.getType(context);
            final var expectedConcatenateType = (PyConcatenateType)expectedParamType;
            final var expectedFirstTypes = expectedConcatenateType.getFirstTypes();

            if (actualParamType instanceof PyConcatenateType) {
              final var actualConcatenateType = (PyConcatenateType)actualParamType;
              final var actualFirstType = actualConcatenateType.getFirstTypes();
              if (!match(expectedFirstTypes, actualFirstType, matchContext)) {
                return Optional.of(false);
              }
            }
            else {
              final var actualParamRightBound = Math.min(expectedFirstTypes.size(), actualParameters.size());
              final var actualFirstParamTypes = ContainerUtil
                .map(actualParameters.subList(0, actualParamRightBound), it -> it.getType(context));

              if (!match(expectedFirstTypes, actualFirstParamTypes, matchContext)) {
                return Optional.of(false);
              }

              if (actualParamRightBound < actualParameters.size()) {
                final var expectedParamSpecType = expectedConcatenateType.getParamSpec();
                final var restActualParameters = actualParameters.subList(actualParamRightBound, actualParameters.size());
                final var parametersSubst = expectedParamSpecType.withParameters(restActualParameters, context);
                matchContext.paramSpecSubstitutions.put(expectedParamSpecType, parametersSubst);
              }
            }

            break;
          }
          else if (expectedParam.isSelf() && actualParam.isSelf()) {
            if (!match(expectedParam.getType(context), actualParam.getType(context), matchContext).orElse(true)) {
              return Optional.of(false);
            }
          }
          else {
            final PyType actualParamType =
              actualParam.isPositionalContainer() && couldBeMappedOntoPositionalContainer(expectedParam)
              ? actualParam.getArgumentType(context)
              : actualParam.getType(context);

            // actual callable type could accept more general parameter type
            if (!match(actualParamType, expectedParam.getType(context), matchContext.reverseSubstitutions()).orElse(true)) {
              return Optional.of(false);
            }
          }
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
      if (type instanceof PyTupleType) {
        final PyTupleType tupleType = (PyTupleType)type;

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

  private static boolean matchGenerics(@NotNull PyCollectionType expected, @NotNull PyType actual, @NotNull MatchContext context) {
    // TODO: Match generic parameters based on the correspondence between the generic parameters of subClass and its base classes
    final List<PyType> superElementTypes = expected.getElementTypes();
    final PyCollectionType actualCollectionType = as(actual, PyCollectionType.class);
    final List<PyType> subElementTypes = actualCollectionType != null ?
                                         actualCollectionType.getElementTypes() :
                                         Collections.emptyList();
    for (int i = 0; i < superElementTypes.size(); i++) {
      final PyType subElementType = i < subElementTypes.size() ? subElementTypes.get(i) : null;
      if (!match(superElementTypes.get(i), subElementType, context).orElse(true)) {
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
    if (type == null || (genericsAreUnknown && type instanceof PyGenericType)) {
      return true;
    }
    if (type instanceof PyFunctionType) {
      final PyCallable callable = ((PyFunctionType)type).getCallable();
      if (callable instanceof PyDecoratable &&
          PyKnownDecoratorUtil.hasChangingReturnTypeDecorator((PyDecoratable)callable, context)) {
        return true;
      }
    }
    if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
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

  @NotNull
  private static Generics collectGenerics(@Nullable PyType type,
                                          @NotNull TypeEvalContext context) {
    final var result = new Generics();
    collectGenerics(type, context, result, new HashSet<>());
    return result;
  }

  private static void collectGenerics(@Nullable PyType type,
                                      @NotNull TypeEvalContext context,
                                      @NotNull Generics generics,
                                      @NotNull Set<? super PyType> visited) {
    if (visited.contains(type)) {
      return;
    }
    visited.add(type);
    if (type instanceof PyGenericType) {
      generics.typeVars.add((PyGenericType)type);
    }
    if (type instanceof PyParamSpecType) {
      generics.paramSpecs.add((PyParamSpecType)type);
    }
    if (type instanceof PyConcatenateType) {
      generics.concatenates.add((PyConcatenateType)type);
    }
    else if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
      for (PyType t : union.getMembers()) {
        collectGenerics(t, context, generics, visited);
      }
    }
    else if (type instanceof PyTupleType) {
      final PyTupleType tuple = (PyTupleType)type;
      final int n = tuple.isHomogeneous() ? 1 : tuple.getElementCount();
      for (int i = 0; i < n; i++) {
        collectGenerics(tuple.getElementType(i), context, generics, visited);
      }
    }
    else if (type instanceof PyCollectionType) {
      final PyCollectionType collection = (PyCollectionType)type;
      for (PyType elementType : collection.getElementTypes()) {
        collectGenerics(elementType, context, generics, visited);
      }
    }
    else if (type instanceof PyCallableType && !(type instanceof PyClassLikeType)) {
      final PyCallableType callable = (PyCallableType)type;
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

  /**
   * @deprecated use {@link PyTypeChecker#substitute(PyType, GenericSubstitutions, TypeEvalContext)} instead
   */
  @Deprecated
  @Nullable
  public static PyType substitute(@Nullable PyType type, @NotNull Map<PyGenericType, PyType> substitutions,
                                  @NotNull TypeEvalContext context) {
    final var genericSubstitutions = new GenericSubstitutions(substitutions, new LinkedHashMap<>());
    return substitute(type, genericSubstitutions, context);
  }

  @Nullable
  public static PyType substitute(@Nullable PyType type,
                                  @NotNull GenericSubstitutions substitutions,
                                  @NotNull TypeEvalContext context) {
    if (hasGenerics(type, context)) {
      if (type instanceof PyGenericType) {
        final PyGenericType typeVar = (PyGenericType)type;
        PyType substitution = substitutions.typeVars.get(typeVar);
        if (substitution == null) {
          final PyInstantiableType<?> invertedTypeVar = invert(typeVar);
          final PyInstantiableType<?> invertedSubstitution = as(substitutions.typeVars.get(invertedTypeVar), PyInstantiableType.class);
          if (invertedSubstitution != null) {
            substitution = invert(invertedSubstitution);
          }
        }
        if (substitution instanceof PyGenericType && !typeVar.equals(substitution) && substitutions.typeVars.containsKey(substitution)) {
          return substitute(substitution, substitutions, context);
        }
        return substitution;
      }
      else if (type instanceof PyUnionType) {
        return ((PyUnionType)type).map(member -> substitute(member, substitutions, context));
      }
      else if (type instanceof PyCollectionTypeImpl) {
        final PyCollectionTypeImpl collection = (PyCollectionTypeImpl)type;
        final List<PyType> elementTypes = collection.getElementTypes();
        final List<PyType> substitutes = new ArrayList<>();
        for (PyType elementType : elementTypes) {
          if (elementType instanceof PyParamSpecType) {
            final var paramSpecType = (PyParamSpecType)elementType;
            final var paramSpecTypeSubst = substitutions.paramSpecs.get(paramSpecType);
            if (paramSpecTypeSubst != null && paramSpecTypeSubst.getParameters() != null) {
              substitutes.add(paramSpecTypeSubst);
            }
          }
          else {
            substitutes.add(substitute(elementType, substitutions, context));
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

        final List<PyType> newElementTypes =
          ContainerUtil.map(oldElementTypes, elementType -> substitute(elementType, substitutions, context));

        return new PyTupleType(tupleClass, newElementTypes, tupleType.isHomogeneous());
      }
      else if (type instanceof PyCallableType && !(type instanceof PyClassLikeType)) {
        final PyCallableType callable = (PyCallableType)type;
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
            if (parameters.size() == 1 && parameterType instanceof PyConcatenateType) {
              final var concatenateType = (PyConcatenateType)parameterType;
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
            final PyType substType = substitute(parameter.getType(context), substitutions, context);
            final PyParameter psi = parameter.getParameter();
            final PyCallableParameter subst = psi != null ?
                                              PyCallableParameterImpl.psi(psi, substType) :
                                              PyCallableParameterImpl.nonPsi(parameter.getName(), substType, parameter.getDefaultValue());
            substParams.add(subst);
          }
        }
        final PyType substResult = substitute(callable.getReturnType(context), substitutions, context);
        return new PyCallableTypeImpl(substParams, substResult);
      }
    }
    return type;
  }

  /**
   * @deprecated use {@link PyTypeChecker#unifyGenericCallWithParamSpecs(PyExpression, Map, TypeEvalContext)} instead
   */
  @Deprecated
  @Nullable
  public static Map<PyGenericType, PyType> unifyGenericCall(@Nullable PyExpression receiver,
                                                            @NotNull Map<PyExpression, PyCallableParameter> arguments,
                                                            @NotNull TypeEvalContext context) {
    final var result = unifyGenericCallWithParamSpecs(receiver, arguments, context);
    if (result == null) return null;
    return result.typeVars;
  }

  @Nullable
  public static GenericSubstitutions unifyGenericCallWithParamSpecs(@Nullable PyExpression receiver,
                                                                    @NotNull Map<PyExpression, PyCallableParameter> arguments,
                                                                    @NotNull TypeEvalContext context) {
    final var substitutions = unifyReceiverWithParamSpecs(receiver, context);
    for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(arguments).entrySet()) {
      final PyCallableParameter paramWrapper = entry.getValue();
      final PyType expectedType = paramWrapper.getArgumentType(context);
      final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(entry.getKey(), expectedType, context, substitutions);
      var actualType = promotedToLiteral != null ? promotedToLiteral : context.getType(entry.getKey());
      if (paramWrapper.isSelf()) {
        // TODO find out a better way to pass the corresponding function inside
        final PyParameter param = paramWrapper.getParameter();
        final PyFunction function = as(ScopeUtil.getScopeOwner(param), PyFunction.class);
        if (function != null && function.getModifier() == PyFunction.Modifier.CLASSMETHOD) {
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
      }
      if (!match(expectedType, actualType, context, substitutions)) {
        return null;
      }
    }
    if (!matchContainer(getMappedPositionalContainer(arguments), getArgumentsMappedToPositionalContainer(arguments),
                        substitutions.typeVars, context)) {
      return null;
    }
    if (!matchContainer(getMappedKeywordContainer(arguments), getArgumentsMappedToKeywordContainer(arguments),
                        substitutions.typeVars, context)) {
      return null;
    }
    return substitutions;
  }

  private static boolean matchContainer(@Nullable PyCallableParameter container, @NotNull List<? extends PyExpression> arguments,
                                        @NotNull Map<PyGenericType, PyType> substitutions, @NotNull TypeEvalContext context) {
    if (container == null) {
      return true;
    }
    final List<PyType> types = ContainerUtil.map(arguments, context::getType);
    return match(container.getArgumentType(context), PyUnionType.union(types), context, substitutions);
  }

  /**
   * @deprecated use {@link PyTypeChecker#unifyReceiverWithParamSpecs(PyExpression, TypeEvalContext)} instead
   */
  @Deprecated
  @NotNull
  public static Map<PyGenericType, PyType> unifyReceiver(@Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    return unifyReceiverWithParamSpecs(receiver, context).typeVars;
  }

  @NotNull
  public static GenericSubstitutions unifyReceiverWithParamSpecs(@Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    // Collect generic params of object type
    final var substitutions = new GenericSubstitutions();
    final PyType qualifierType = receiver != null ? context.getType(receiver) : null;
    final var generics = collectGenerics(qualifierType, context);
    for (PyGenericType t : generics.typeVars) {
      substitutions.typeVars.put(t, t);
    }
    for (PyParamSpecType p : generics.paramSpecs) {
      substitutions.paramSpecs.put(p, p);
    }
    if (qualifierType != null) {
      for (PyClassType type : PyTypeUtil.toStream(qualifierType).select(PyClassType.class)) {
        for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
          final PyType genericType = provider.getGenericType(type.getPyClass(), context);
          Generics providedGenerics = new Generics();

          if (genericType != null) {
            match(genericType, type, context, substitutions);
            providedGenerics = collectGenerics(genericType, context);
          }

          for (Map.Entry<PyType, PyType> entry : provider.getGenericSubstitutions(type.getPyClass(), context).entrySet()) {
            final PyGenericType genericKey = as(entry.getKey(), PyGenericType.class);
            final PyType value = entry.getValue();

            if (genericKey != null &&
                value != null &&
                !substitutions.typeVars.containsKey(genericKey) &&
                !providedGenerics.typeVars.contains(genericKey)) {
              substitutions.typeVars.put(genericKey, value);
            }
          }
        }
      }
    }

    replaceUnresolvedGenericsWithAny(substitutions.typeVars);
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
      final List<PyGenericType> formalTypeParams = Lists.newArrayList(typeParams.typeVars);
      final Map<PyGenericType, PyType> substitutions = new HashMap<>();
      for (int i = 0; i < Math.min(formalTypeParams.size(), actualTypeParams.size()); i++) {
        substitutions.put(formalTypeParams.get(i), actualTypeParams.get(i));
      }
      return substitute(genericType, new GenericSubstitutions(substitutions, Collections.emptyMap()), context);
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

  private static class Generics {
    @NotNull
    private final Set<PyGenericType> typeVars;

    @NotNull
    private final Set<PyParamSpecType> paramSpecs;

    @NotNull
    private final Set<PyConcatenateType> concatenates;

    Generics() {
      this(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    Generics(@NotNull Set<PyGenericType> generics, @NotNull Set<PyParamSpecType> paramSpecs, @NotNull Set<PyConcatenateType> concatenates) {
      this.typeVars = generics;
      this.paramSpecs = paramSpecs;
      this.concatenates = concatenates;
    }

    boolean isEmpty() {
      return typeVars.isEmpty() && paramSpecs.isEmpty() && concatenates.isEmpty();
    }
  }

  public static class GenericSubstitutions {
    @NotNull
    private final Map<PyGenericType, PyType> typeVars;

    @NotNull
    private final Map<PyParamSpecType, PyParamSpecType> paramSpecs;

    GenericSubstitutions() {
      this(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    GenericSubstitutions(@NotNull Map<PyGenericType, PyType> typeVars,
                         @NotNull Map<PyParamSpecType, PyParamSpecType> paramSpecs) {
      this.typeVars = typeVars;
      this.paramSpecs = paramSpecs;
    }

    @NotNull
    public Map<PyParamSpecType, PyParamSpecType> getParamSpecs() {
      return paramSpecs;
    }

    @NotNull
    public Map<PyGenericType, PyType> getTypeVars() {
      return typeVars;
    }
  }

  private static class MatchContext {

    @NotNull
    private final TypeEvalContext context;

    @NotNull
    private final Map<PyGenericType, PyType> genericSubstitutions; // mutable

    @NotNull
    private final Map<PyParamSpecType, PyParamSpecType> paramSpecSubstitutions; // mutable

    private final boolean reversedSubstitutions;

    MatchContext(@NotNull TypeEvalContext context,
                 @NotNull Map<PyGenericType, PyType> genericSubstitutions) {
      this(context, genericSubstitutions, false);
    }

    private MatchContext(@NotNull TypeEvalContext context,
                         @NotNull Map<PyGenericType, PyType> genericSubstitutions,
                         boolean reversedSubstitutions) {
      this(context, genericSubstitutions, new HashMap<>(), reversedSubstitutions);
    }

    private MatchContext(@NotNull TypeEvalContext context,
                         @NotNull Map<PyGenericType, PyType> genericSubstitutions,
                         @NotNull Map<PyParamSpecType, PyParamSpecType> paramSpecSubstitutions,
                         boolean reversedSubstitutions) {
      this.context = context;
      this.genericSubstitutions = genericSubstitutions;
      this.paramSpecSubstitutions = paramSpecSubstitutions;
      this.reversedSubstitutions = reversedSubstitutions;
    }

    @NotNull
    public MatchContext reverseSubstitutions() {
      return new MatchContext(context, genericSubstitutions, !reversedSubstitutions);
    }
  }
}
