package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PyTypeParameterMapping {
  private final List<Couple<PyType>> myMappedTypes;

  private PyTypeParameterMapping(@NotNull List<Couple<PyType>> mapping) {
    for (Couple<PyType> couple : mapping) {
      PyType expectedType = couple.getFirst();
      PyType actualType = couple.getSecond();
      if (expectedType != null && actualType != null) {
        if (expectedType instanceof PyPositionalVariadicType ^ actualType instanceof PyPositionalVariadicType ||
            expectedType instanceof PyCallableParameterVariadicType ^ actualType instanceof PyCallableParameterVariadicType) {
          throw new IllegalArgumentException("Mapping of incompatible types: " + expectedType + " -> " + actualType);
        }
      }
    }
    myMappedTypes = mapping;
  }

  public static @Nullable PyTypeParameterMapping mapWithParameterList(@NotNull List<? extends PyType> expectedParameterTypes,
                                                                      @NotNull List<PyCallableParameter> actualParameters,
                                                                      @NotNull TypeEvalContext context) {
    List<PyType> flattenedExpectedParameterTypes = flattenUnpackedTupleTypes(expectedParameterTypes);
    int expectedArity = ContainerUtil.exists(flattenedExpectedParameterTypes, Conditions.instanceOf(PyPositionalVariadicType.class))
                        ? -1
                        : flattenedExpectedParameterTypes.size();

    List<PyType> requiredPositionalArgumentTypes = new ArrayList<>();
    List<PyType> optionalPositionalArgumentTypes = new ArrayList<>();
    List<PyType> positionalVarargArgumentTypes = new SmartList<>();

    for (PyCallableParameter parameter : actualParameters) {
      if (parameter.isSelf()
          || parameter.getParameter() instanceof PySlashParameter
          || parameter.getParameter() instanceof PySingleStarParameter
          || parameter.isKeywordContainer()) {
        continue;
      }
      if (parameter.getParameter() instanceof PyNamedParameter namedParameter && namedParameter.isKeywordOnly()) {
        if (!namedParameter.hasDefaultValue()) {
          return null;
        }
        continue;
      }
      PyType actualParameterType = parameter.getType(context);
      if (parameter.isPositionalContainer()) {
        // Convert an automatic tuple[MyType, ...] to *tuple[MyType, ...] for matching purposes
        if (actualParameterType instanceof PyTupleType argsTupleType) {
          positionalVarargArgumentTypes.addAll(flattenUnpackedTupleTypes(Collections.singletonList(argsTupleType.asUnpackedTupleType())));
        }
        else {
          positionalVarargArgumentTypes.addAll(flattenUnpackedTupleTypes(Collections.singletonList(actualParameterType)));
        }
      }
      else if (parameter.hasDefaultValue()) {
        optionalPositionalArgumentTypes.add(actualParameterType);
      }
      else {
        requiredPositionalArgumentTypes.addAll(flattenUnpackedTupleTypes(Collections.singletonList(actualParameterType)));
      }
    }

    if (positionalVarargArgumentTypes.size() > 1 ||
        positionalVarargArgumentTypes.size() == 1 && !(positionalVarargArgumentTypes.get(0) instanceof PyPositionalVariadicType)) {
      requiredPositionalArgumentTypes.addAll(optionalPositionalArgumentTypes);
      optionalPositionalArgumentTypes.clear();
      requiredPositionalArgumentTypes.addAll(positionalVarargArgumentTypes);
      positionalVarargArgumentTypes.clear();
    }

    int actualArity = ContainerUtil.exists(requiredPositionalArgumentTypes, Conditions.instanceOf(PyPositionalVariadicType.class)) ?
                      -1 :
                      requiredPositionalArgumentTypes.size();


    if (expectedArity != -1 && actualArity != -1) {
      if (actualArity > expectedArity) {
        return null;
      }
      List<PyType> arityAdjustedActualParameterTypes = new ArrayList<>(requiredPositionalArgumentTypes);
      arityAdjustedActualParameterTypes.addAll(optionalPositionalArgumentTypes.subList(
        0, Math.min(optionalPositionalArgumentTypes.size(), expectedArity - arityAdjustedActualParameterTypes.size())
      ));
      if (!positionalVarargArgumentTypes.isEmpty() && expectedArity - arityAdjustedActualParameterTypes.size() > 0) {
        assert positionalVarargArgumentTypes.size() == 1 && positionalVarargArgumentTypes.get(0) instanceof PyPositionalVariadicType;
        arityAdjustedActualParameterTypes.add(positionalVarargArgumentTypes.get(0));
      }
      return mapByShape(flattenedExpectedParameterTypes, arityAdjustedActualParameterTypes);
    }
    return mapByShape(flattenedExpectedParameterTypes,
                      ContainerUtil.concat(requiredPositionalArgumentTypes,
                                           optionalPositionalArgumentTypes,
                                           positionalVarargArgumentTypes));
  }

  public static @Nullable PyTypeParameterMapping mapByShape(@NotNull List<? extends PyType> expectedTypes,
                                                            @NotNull List<? extends PyType> actualTypes,
                                                            Option @NotNull ... options) {
    EnumSet<Option> optionSet = EnumSet.noneOf(Option.class);
    optionSet.addAll(Arrays.asList(options));

    List<PyType> normalizedExpectedTypes = flattenUnpackedTupleTypes(expectedTypes);
    List<PyType> normalizedActualTypes =
      replaceExpectedTypesWithParameterList(normalizedExpectedTypes, flattenUnpackedTupleTypes(actualTypes));

    NullTolerantDeque<PyType> expectedTypesDeque = new NullTolerantDeque<>(normalizedExpectedTypes);
    NullTolerantDeque<PyType> actualTypesDeque = new NullTolerantDeque<>(normalizedActualTypes);

    List<Couple<PyType>> leftMappedTypes = new ArrayList<>();
    List<Couple<PyType>> centerMappedTypes = new ArrayList<>();
    List<Couple<PyType>> rightMappedTypes = new ArrayList<>();

    boolean splittingTypeVarTuple = false;
    while (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0) {
      PyType leftmostExpected = expectedTypesDeque.peekFirst();
      // Either a variadic type parameter *Ts or an unbounded unpacked tuple *tuple[int, ...] 
      if (leftmostExpected instanceof PyPositionalVariadicType) {
        break;
      }
      PyType leftmostActual = actualTypesDeque.peekFirst();
      if (leftmostExpected != null &&
          leftmostActual != null &&
          leftmostExpected instanceof PyCallableParameterVariadicType ^ leftmostActual instanceof PyCallableParameterVariadicType) {
        break;
      }
      // The leftmost expected type is a regular type, not variadic
      if (leftmostActual instanceof PyPositionalVariadicType) {
        break;
      }
      expectedTypesDeque.removeFirst();
      actualTypesDeque.removeFirst();
      leftMappedTypes.add(Couple.of(leftmostExpected, leftmostActual));
    }

    while (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0) {
      PyType rightmostExpected = expectedTypesDeque.peekLast();
      if (rightmostExpected instanceof PyPositionalVariadicType) {
        break;
      }
      PyType rightmostActual = actualTypesDeque.peekLast();
      if (rightmostExpected != null &&
          rightmostActual != null &&
          rightmostExpected instanceof PyCallableParameterVariadicType ^ rightmostActual instanceof PyCallableParameterVariadicType) {
        break;
      }
      expectedTypesDeque.removeLast();
      if (rightmostActual instanceof PyPositionalVariadicType rightmostActualVariadic) {
        // [T1, T2] <- [*tuple[T3, ...]]
        if (rightmostActualVariadic instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound()) {
          PyType repeatedActualType = unpackedTupleType.getElementTypes().get(0);
          rightMappedTypes.add(Couple.of(rightmostExpected, repeatedActualType));
        }
        // [T1, T2] <- [*Ts]
        else {
          splittingTypeVarTuple = true;
          break;
        }
      }
      else {
        actualTypesDeque.removeLast();
        rightMappedTypes.add(Couple.of(rightmostExpected, rightmostActual));
      }
    }

    // [T1, T2] <- [*tuple[T3, ...]]
    if (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0
        && !(expectedTypesDeque.peekFirst() instanceof PyVariadicType)
        && actualTypesDeque.peekFirst() instanceof PyPositionalVariadicType actualPositionalVariadic) {
      if (actualPositionalVariadic instanceof PyUnpackedTupleType actualUnpackedTupleType && actualUnpackedTupleType.isUnbound()) {
        while (expectedTypesDeque.size() != 0 && !(expectedTypesDeque.peekFirst() instanceof PyVariadicType)) {
          PyType repeatedActualType = actualUnpackedTupleType.getElementTypes().get(0);
          leftMappedTypes.add(Couple.of(expectedTypesDeque.peekFirst(), repeatedActualType));
          expectedTypesDeque.removeFirst();
        }
      }
      else {
        splittingTypeVarTuple = true;
      }
    }

    if (splittingTypeVarTuple) {
      return null;
    }

    if (expectedTypesDeque.size() != 0 && expectedTypesDeque.peekFirst() instanceof PyPositionalVariadicType expectedPositionalVariadic) {
      // [*Ts] <- [*Ts] or [*Ts] <- [*tuple[T1, ...]]
      if (actualTypesDeque.size() == 1 && actualTypesDeque.peekFirst() instanceof PyPositionalVariadicType variadicType) {
        expectedTypesDeque.removeFirst();
        actualTypesDeque.removeFirst();
        centerMappedTypes.add(Couple.of(expectedPositionalVariadic, variadicType));
      }
      // [*Ts=Unpacked[tuple[int, str]]] <- []
      else if (actualTypesDeque.size() == 0 && optionSet.contains(Option.USE_DEFAULTS)
               && expectedPositionalVariadic instanceof PyTypeVarTupleType typeVarTupleType
               && typeVarTupleType.getDefaultType() != null) {
        expectedTypesDeque.removeFirst();
        centerMappedTypes.add(Couple.of(expectedPositionalVariadic, Ref.deref(typeVarTupleType.getDefaultType())));
      }
      // [*Ts] <- [T1, *Ts[T2, ...], T2, ...]
      else {
        List<PyType> nonParamVariadicActualTypes = new ArrayList<>();
        while (actualTypesDeque.size() != 0 && !(actualTypesDeque.peekFirst() instanceof PyCallableParameterVariadicType)) {
          nonParamVariadicActualTypes.add(actualTypesDeque.peekFirst());
          actualTypesDeque.removeFirst();
        }
        expectedTypesDeque.removeFirst();
        centerMappedTypes.add(Couple.of(expectedPositionalVariadic, PyUnpackedTupleTypeImpl.create(nonParamVariadicActualTypes)));
      }
    }

    boolean sizeMismatch = true;
    if (expectedTypesDeque.size() == 0) {
      boolean allActualTypesMatched = actualTypesDeque.size() == 0;
      boolean onlySingleActualVariadicLeft = actualTypesDeque.size() == 1 &&
                                             actualTypesDeque.peekFirst() instanceof PyPositionalVariadicType;
      sizeMismatch = !(allActualTypesMatched || onlySingleActualVariadicLeft);
    }
    else if (actualTypesDeque.size() == 0) {
      // [T1, T2, ...] <- []
      boolean allMapped = true;
      for (PyType unmatchedType : expectedTypesDeque.toList()) {
        Couple<PyType> fallbackMapping = mapToFallback(unmatchedType, optionSet);
        allMapped = fallbackMapping != null;
        if (!allMapped) {
          break;
        }
        centerMappedTypes.add(fallbackMapping);
      }
      sizeMismatch = !allMapped;
    }
    if (sizeMismatch) {
      return null;
    }
    List<Couple<PyType>> resultMapping = new ArrayList<>(leftMappedTypes);
    resultMapping.addAll(centerMappedTypes);
    Collections.reverse(rightMappedTypes);
    resultMapping.addAll(rightMappedTypes);
    return new PyTypeParameterMapping(resultMapping);
  }

  // [**P] <- [int, str, bool] is equivalent to [**P] <- [[int, str, bool]]
  // See https://typing.readthedocs.io/en/latest/spec/generics.html#user-defined-generic-classes
  private static @NotNull List<PyType> replaceExpectedTypesWithParameterList(@NotNull List<PyType> expectedTypes,
                                                                             @NotNull List<PyType> actualTypes) {
    if (ContainerUtil.getOnlyItem(expectedTypes) instanceof PyParamSpecType && 
        !actualTypes.isEmpty() && !ContainerUtil.exists(actualTypes, o -> o instanceof PyVariadicType)) {
      PyCallableParameterListType callableParameterListType =
        new PyCallableParameterListTypeImpl(ContainerUtil.map(actualTypes, PyCallableParameterImpl::nonPsi));
      return Collections.singletonList(callableParameterListType);
    }
    return actualTypes;
  }

  private static @Nullable Couple<PyType> mapToFallback(@Nullable PyType unmatchedExpectedType, @NotNull EnumSet<Option> optionSet) {
    if (optionSet.contains(Option.USE_DEFAULTS) &&
        unmatchedExpectedType instanceof PyTypeParameterType typeParameterType &&
        typeParameterType.getDefaultType() != null) {
      return Couple.of(unmatchedExpectedType, Ref.deref(typeParameterType.getDefaultType()));
    }
    else if (optionSet.contains(Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY)) {
      return Couple.of(unmatchedExpectedType, null);
    }
    return null;
  }

  private static @NotNull List<PyType> flattenUnpackedTupleTypes(List<? extends PyType> types) {
    return ContainerUtil.flatMap(types, type -> {
      if (type instanceof PyUnpackedTupleType unpackedTupleType && !unpackedTupleType.isUnbound()) {
        return flattenUnpackedTupleTypes(unpackedTupleType.getElementTypes());
      }
      return Collections.singletonList(type);
    });
  }

  public @NotNull List<Couple<PyType>> getMappedTypes() {
    return Collections.unmodifiableList(myMappedTypes);
  }

  public enum Option {
    MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
    USE_DEFAULTS,
  }

  private static final class NullTolerantDeque<T> {
    private final Deque<Ref<T>> myDeque;

    private NullTolerantDeque(@NotNull Collection<? extends @Nullable T> collection) {
      myDeque = new ArrayDeque<>(ContainerUtil.map(collection, Ref::create));
    }

    public @Nullable T peekFirst() {
      if (myDeque.isEmpty()) throw new NoSuchElementException();
      return Ref.deref(myDeque.peekFirst());
    }

    public @Nullable T peekLast() {
      if (myDeque.isEmpty()) throw new NoSuchElementException();
      return Ref.deref(myDeque.peekLast());
    }

    public void removeFirst() {
      if (myDeque.isEmpty()) throw new NoSuchElementException();
      myDeque.removeFirst();
    }

    public void removeLast() {
      if (myDeque.isEmpty()) throw new NoSuchElementException();
      myDeque.removeLast();
    }

    public int size() {
      return myDeque.size();
    }

    public @NotNull List<@Nullable T> toList() {
      return ContainerUtil.map(myDeque, Ref::deref);
    }
  }

  @Override
  public String toString() {
    return StringUtil.join(myMappedTypes, pair -> pair.first + " -> " + pair.second, ", ");
  }
}
