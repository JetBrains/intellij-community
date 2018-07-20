// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.pyi.PyiFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.codeInsight.typing.PyProtocolsKt.inspectProtocolSubclass;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;

/**
 * @author vlan
 */
public class PyTypeChecker {
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

  @NotNull
  private static Optional<Boolean> match(@Nullable PyType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    final Pair<PyType, PyType> types = Pair.create(expected, actual);
    if (context.matching.contains(types)) return Optional.of(true);

    context.matching.add(types);
    final Optional<Boolean> result = matchImpl(expected, actual, context);
    context.matching.remove(types);

    return result;
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
    if (expected instanceof PyClassType) {
      Optional<Boolean> match = matchObject((PyClassType)expected, actual);
      if (match.isPresent()) {
        return match;
      }
    }

    if (expected instanceof PyInstantiableType && actual instanceof PyInstantiableType) {
      Optional<Boolean> match = match((PyInstantiableType)expected, (PyInstantiableType)actual, context);
      if (match.isPresent()) {
        return match;
      }
    }

    if (expected instanceof PyGenericType) {
      return Optional.of(match((PyGenericType)expected, actual, context));
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
          actual instanceof PyInstantiableType && ((PyInstantiableType)actual).isDefinition()) {
        return Optional.of(true);
      }
    }
    return Optional.empty();
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyInstantiableType expected, @NotNull PyInstantiableType actual,
                                         @NotNull MatchContext context) {
    if (expected instanceof PyGenericType && typeVarAcceptsBothClassAndInstanceTypes((PyGenericType)expected)) {
      return Optional.empty();
    }

    if (expected.isDefinition() ^ actual.isDefinition()) {
      if (actual.isDefinition() &&
          actual instanceof PyClassLikeType &&
          matchClassObjectAndMetaclass(expected, (PyClassLikeType)actual, context)) {
        return Optional.of(true);
      }
      return Optional.of(false);
    }

    return Optional.empty();
  }

  /**
   * Match {@code actual} versus {@code PyGenericType expected}.
   *
   * The method mutates {@code context.substitutions} map adding new entries into it
   */
  private static boolean match(@NotNull PyGenericType expected, @Nullable PyType actual, @NotNull MatchContext context) {
    final PyType substitution = context.substitutions.get(expected);
    PyType bound = expected.getBound();
    // Promote int in Type[TypeVar('T', int)] to Type[int] before checking that bounds match
    if (expected.isDefinition() && bound instanceof PyInstantiableType) {
      bound = ((PyInstantiableType)bound).toClass();
    }

    Optional<Boolean> match = match(bound, actual, context);
    if (match.isPresent() && !match.get()) {
      return false;
    }

    if (substitution != null) {
      if (expected.equals(actual) || substitution.equals(expected)) {
        return true;
      }
      if (context.recursive) {
        Optional<Boolean> recursiveMatch = match(substitution, actual, context.notRecursive());
        if (recursiveMatch.isPresent()) {
          return recursiveMatch.get();
        }
      }
      return false;
    }

    if (actual != null) {
      context.substitutions.put(expected, actual);
    }
    else if (bound != null) {
      context.substitutions.put(expected, bound);
    }

    return true;
  }

  private static boolean match(@NotNull PyType expected, @NotNull PyUnionType actual, @NotNull MatchContext context) {
    if (expected instanceof PyTupleType) {
      Optional<Boolean> match = match((PyTupleType)expected, actual, context);
      if (match.isPresent()) {
        return match.get();
      }
    }

    return StreamEx.of(actual.getMembers()).anyMatch(type -> match(expected, type, context).orElse(false));
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
    return StreamEx.of(expected.getMembers()).anyMatch(type -> match(type, actual, context).orElse(true));
  }

  @NotNull
  private static Optional<Boolean> match(@NotNull PyClassType expected, @NotNull PyClassType actual, @NotNull MatchContext context) {
    if (expected.equals(actual)) {
      return Optional.of(true);
    }

    if (expected instanceof PyTupleType && actual instanceof PyTupleType) {
      return match((PyTupleType)expected, (PyTupleType)actual, context);
    }

    final PyClass superClass = expected.getPyClass();
    final PyClass subClass = actual.getPyClass();
    final boolean matchClasses = matchClasses(superClass, subClass, context.context);

    if (PyProtocolsKt.isProtocol(expected, context.context) && !matchClasses) {
      if (expected instanceof PyCollectionType && !matchGenerics((PyCollectionType)expected, actual, context)) {
        return Optional.of(false);
      }

      for (kotlin.Pair<PyTypedElement, List<RatedResolveResult>> pair : inspectProtocolSubclass(expected, actual, context.context)) {
        final List<RatedResolveResult> subclassElements = pair.getSecond();
        if (ContainerUtil.isEmpty(subclassElements)) {
          return Optional.of(false);
        }

        final PyType protocolElementType = context.context.getType(pair.getFirst());

        final boolean elementResult = StreamEx
          .of(subclassElements)
          .map(ResolveResult::getElement)
          .select(PyTypedElement.class)
          .map(context.context::getType)
          .anyMatch(subclassElementType -> match(protocolElementType, subclassElementType, context).orElse(true));

        if (!elementResult) {
          return Optional.of(false);
        }
      }

      final PyType originalProtocolGenericType = StreamEx
        .of(Extensions.getExtensions(PyTypeProvider.EP_NAME))
        .map(provider -> provider.getGenericType(superClass, context.context))
        .findFirst(Objects::nonNull)
        .orElse(null);

      // actual was matched against protocol definition above
      // and here protocol usage is matched against its definition to update substitutions
      match(expected, originalProtocolGenericType, context);

      return Optional.of(true);
    }

    if (expected instanceof PyCollectionType) {
      return Optional.of(match((PyCollectionType)expected, actual, context));
    }

    if (matchClasses) {
      if (expected instanceof PyTypingNewType && !expected.equals(actual) && superClass.equals(subClass)) {
        return Optional.of(actual.getAncestorTypes(context.context).contains(expected));
      }
      return Optional.of(true);
    }

    if (expected.equals(actual)) {
      return Optional.of(true);
    }
    return Optional.empty();
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

    if (!match(superElementType, subElementType, context).orElse(true)) {
      return false;
    }

    return true;
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

    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    return StreamEx
      .of(expected.getAttributeNames())
      .noneMatch(attribute -> ContainerUtil.isEmpty(actual.resolveMember(attribute, null, AccessDirection.READ, resolveContext)));
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
  private static Optional<Boolean> match(@NotNull PyCallableType expected, @NotNull PyCallableType actual, @NotNull MatchContext context) {
    if (expected.isCallable() && actual.isCallable()) {
      final List<PyCallableParameter> expectedParameters = expected.getParameters(context.context);
      final List<PyCallableParameter> actualParameters = actual.getParameters(context.context);
      if (expectedParameters != null && actualParameters != null) {
        final int size = Math.min(expectedParameters.size(), actualParameters.size());
        for (int i = 0; i < size; i++) {
          final PyCallableParameter expectedParam = expectedParameters.get(i);
          final PyCallableParameter actualParam = actualParameters.get(i);
          // TODO: Check named and star params, not only positional ones
          if (!match(expectedParam.getType(context.context), actualParam.getType(context.context), context).orElse(true)) {
            return Optional.of(false);
          }
        }
      }
      if (!match(expected.getReturnType(context.context), actual.getReturnType(context.context), context).orElse(true)) {
        return Optional.of(false);
      }
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private static boolean matchClassObjectAndMetaclass(@NotNull PyType expected,
                                                      @NotNull PyClassLikeType actual,
                                                      @NotNull MatchContext context) {

    if (!actual.isDefinition()) {
      return false;
    }
    final PyClassLikeType metaClass = actual.getMetaClassType(context.context, true);
    return metaClass != null && match(expected, metaClass, context).orElse(true);
  }

  private static boolean typeVarAcceptsBothClassAndInstanceTypes(@NotNull PyGenericType typeVar) {
    return !typeVar.isDefinition() && typeVar.getBound() == null;
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
    final String superName = expected.getName();
    final String subName = actual.getName();
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
          PyKnownDecoratorUtil.hasUnknownOrChangingReturnTypeDecorator((PyDecoratable)callable, context)) {
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

  @Nullable
  public static PyType toNonWeakType(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)type;
      if (unionType.isWeak()) {
        return unionType.excludeNull(context);
      }
    }
    return type;
  }

  public static boolean hasGenerics(@Nullable PyType type, @NotNull TypeEvalContext context) {
    final Set<PyGenericType> collected = new HashSet<>();
    collectGenerics(type, context, collected, new HashSet<>());
    return !collected.isEmpty();
  }

  private static void collectGenerics(@Nullable PyType type, @NotNull TypeEvalContext context, @NotNull Set<PyGenericType> collected,
                                      @NotNull Set<PyType> visited) {
    if (visited.contains(type)) {
      return;
    }
    visited.add(type);
    if (type instanceof PyGenericType) {
      collected.add((PyGenericType)type);
    }
    else if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
      for (PyType t : union.getMembers()) {
        collectGenerics(t, context, collected, visited);
      }
    }
    else if (type instanceof PyTupleType) {
      final PyTupleType tuple = (PyTupleType)type;
      final int n = tuple.isHomogeneous() ? 1 : tuple.getElementCount();
      for (int i = 0; i < n; i++) {
        collectGenerics(tuple.getElementType(i), context, collected, visited);
      }
    }
    else if (type instanceof PyCollectionType) {
      final PyCollectionType collection = (PyCollectionType)type;
      for (PyType elementType : collection.getElementTypes()) {
        collectGenerics(elementType, context, collected, visited);
      }
    }
    else if (type instanceof PyCallableType) {
      final PyCallableType callable = (PyCallableType)type;
      final List<PyCallableParameter> parameters = callable.getParameters(context);
      if (parameters != null) {
        for (PyCallableParameter parameter : parameters) {
          if (parameter != null) {
            collectGenerics(parameter.getType(context), context, collected, visited);
          }
        }
      }
      collectGenerics(callable.getReturnType(context), context, collected, visited);
    }
  }

  @Nullable
  public static PyType substitute(@Nullable PyType type, @NotNull Map<PyGenericType, PyType> substitutions,
                                  @NotNull TypeEvalContext context) {
    if (hasGenerics(type, context)) {
      if (type instanceof PyGenericType) {
        final PyGenericType typeVar = (PyGenericType)type;
        PyType substitution = substitutions.get(typeVar);
        if (substitution == null) {
          if (!typeVar.isDefinition()) {
            final PyInstantiableType<?> classType = as(substitutions.get(typeVar.toClass()), PyInstantiableType.class);
            if (classType != null) {
              substitution = classType.toInstance();
            }
          }
          else {
            final PyInstantiableType<?> instanceType = as(substitutions.get(typeVar.toInstance()), PyInstantiableType.class);
            if (instanceType != null) {
              substitution = instanceType.toClass();
            }
          }
        }
        if (substitution instanceof PyGenericType && !typeVar.equals(substitution) && substitutions.containsKey(substitution)) {
          return substitute(substitution, substitutions, context);
        }
        return substitution;
      }
      else if (type instanceof PyUnionType) {
        final PyUnionType union = (PyUnionType)type;
        final List<PyType> results = new ArrayList<>();
        for (PyType t : union.getMembers()) {
          final PyType subst = substitute(t, substitutions, context);
          results.add(subst);
        }
        return PyUnionType.union(results);
      }
      else if (type instanceof PyCollectionTypeImpl) {
        final PyCollectionTypeImpl collection = (PyCollectionTypeImpl)type;
        final List<PyType> elementTypes = collection.getElementTypes();
        final List<PyType> substitutes = new ArrayList<>();
        for (PyType elementType : elementTypes) {
          substitutes.add(substitute(elementType, substitutions, context));
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
      else if (type instanceof PyCallableType) {
        final PyCallableType callable = (PyCallableType)type;
        List<PyCallableParameter> substParams = null;
        final List<PyCallableParameter> parameters = callable.getParameters(context);
        if (parameters != null) {
          substParams = new ArrayList<>();
          for (PyCallableParameter parameter : parameters) {
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

  @Nullable
  public static Map<PyGenericType, PyType> unifyGenericCall(@Nullable PyExpression receiver,
                                                            @NotNull Map<PyExpression, PyCallableParameter> arguments,
                                                            @NotNull TypeEvalContext context) {
    final Map<PyGenericType, PyType> substitutions = unifyReceiver(receiver, context);
    for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(arguments).entrySet()) {
      final PyCallableParameter paramWrapper = entry.getValue();
      PyType actualType = context.getType(entry.getKey());
      if (paramWrapper.isSelf()) {
        // TODO find out a better way to pass the corresponding function inside
        final PyParameter param = paramWrapper.getParameter();
        final PyFunction function = as(ScopeUtil.getScopeOwner(param), PyFunction.class);
        if (function != null && function.getModifier() == PyFunction.Modifier.CLASSMETHOD) {
          final StreamEx<PyType> types;
          if (actualType instanceof PyUnionType) {
            types = StreamEx.of(((PyUnionType)actualType).getMembers());
          }
          else {
            types = StreamEx.of(actualType);
          }
          actualType = types
            .select(PyClassLikeType.class)
            .map(PyClassLikeType::toClass)
            .select(PyType.class)
            .foldLeft(PyUnionType::union)
            .orElse(actualType);
        }
      }
      final PyType expectedType = paramWrapper.getArgumentType(context);
      if (!match(expectedType, actualType, context, substitutions)) {
        return null;
      }
    }
    if (!matchContainer(getMappedPositionalContainer(arguments), getArgumentsMappedToPositionalContainer(arguments), substitutions,
                        context)) {
      return null;
    }
    if (!matchContainer(getMappedKeywordContainer(arguments), getArgumentsMappedToKeywordContainer(arguments), substitutions, context)) {
      return null;
    }
    return substitutions;
  }

  private static boolean matchContainer(@Nullable PyCallableParameter container, @NotNull List<PyExpression> arguments,
                                        @NotNull Map<PyGenericType, PyType> substitutions, @NotNull TypeEvalContext context) {
    if (container == null) {
      return true;
    }
    final List<PyType> types = ContainerUtil.map(arguments, context::getType);
    return match(container.getArgumentType(context), PyUnionType.union(types), context, substitutions);
  }

  @NotNull
  public static Map<PyGenericType, PyType> unifyReceiver(@Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    final Map<PyGenericType, PyType> substitutions = new LinkedHashMap<>();
    // Collect generic params of object type
    final Set<PyGenericType> generics = new LinkedHashSet<>();
    final PyType qualifierType = receiver != null ? context.getType(receiver) : null;
    collectGenerics(qualifierType, context, generics, new HashSet<>());
    for (PyGenericType t : generics) {
      substitutions.put(t, t);
    }
    if (qualifierType != null) {
      for (PyClassType type : toPossibleClassTypes(qualifierType)) {
        for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
          final PyType genericType = provider.getGenericType(type.getPyClass(), context);
          final Set<PyGenericType> providedTypeGenerics = new LinkedHashSet<>();

          if (genericType != null) {
            match(genericType, type, context, substitutions);
            collectGenerics(genericType, context, providedTypeGenerics, new HashSet<>());
          }

          for (Map.Entry<PyType, PyType> entry : provider.getGenericSubstitutions(type.getPyClass(), context).entrySet()) {
            final PyGenericType genericKey = as(entry.getKey(), PyGenericType.class);
            final PyType value = entry.getValue();

            if (genericKey != null &&
                value != null &&
                !substitutions.containsKey(genericKey) &&
                !providedTypeGenerics.contains(genericKey)) {
              substitutions.put(genericKey, value);
            }
          }
        }
      }
    }

    replaceUnresolvedGenericsWithAny(substitutions);
    return substitutions;
  }

  @NotNull
  private static List<PyClassType> toPossibleClassTypes(@NotNull PyType type) {
    final PyClassType classType = as(type, PyClassType.class);
    if (classType != null) {
      return Collections.singletonList(classType);
    }
    final PyUnionType unionType = as(type, PyUnionType.class);
    if (unionType != null) {
      return StreamEx.of(unionType.getMembers()).nonNull().flatMap(t -> toPossibleClassTypes(t).stream()).toList();
    }
    return Collections.emptyList();
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
                          ? PyBuiltinCache.getInstance(subClass).getObjectType(PyNames.TYPE_UNICODE) != null
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
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
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

  private static class MatchContext {

    @NotNull
    private final TypeEvalContext context;

    @NotNull
    private final Map<PyGenericType, PyType> substitutions; // mutable

    private final boolean recursive;

    @NotNull
    private final Set<Pair<PyType, PyType>> matching; // mutable

    public MatchContext(@NotNull TypeEvalContext context,
                        @NotNull Map<PyGenericType, PyType> substitutions) {
      this(context, substitutions, true, new HashSet<>());
    }

    private MatchContext(@NotNull TypeEvalContext context,
                         @NotNull Map<PyGenericType, PyType> substitutions,
                         boolean recursive,
                         @NotNull Set<Pair<PyType, PyType>> matching) {
      this.context = context;
      this.substitutions = substitutions;
      this.recursive = recursive;
      this.matching = matching;
    }

    @NotNull
    public MatchContext notRecursive() {
      return new MatchContext(context, substitutions, false, matching);
    }
  }
}
