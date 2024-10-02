package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
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
      if (expectedType instanceof PyVariadicType && !(actualType instanceof PyVariadicType || actualType == null)) {
        throw new IllegalArgumentException("Variadic type " + expectedType + " cannot be mapped to a non-variadic type " + actualType);
      }
      if (!(expectedType instanceof PyVariadicType) && actualType instanceof PyVariadicType) {
        throw new IllegalArgumentException("Non-variadic type " + expectedType + " cannot be mapped to a variadic type " + actualType);
      }
    }
    myMappedTypes = mapping;
  }

  public static @Nullable PyTypeParameterMapping mapWithParameterList(@NotNull List<? extends PyType> expectedParameterTypes,
                                                                      @NotNull List<PyCallableParameter> actualParameters,
                                                                      @NotNull TypeEvalContext context) {
    List<PyType> flattenedExpectedParameterTypes = flattenUnpackedTupleTypes(expectedParameterTypes);
    int expectedArity = ContainerUtil.exists(flattenedExpectedParameterTypes, Conditions.instanceOf(PyVariadicType.class))
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
        positionalVarargArgumentTypes.size() == 1 && !(positionalVarargArgumentTypes.get(0) instanceof PyVariadicType)) {
      requiredPositionalArgumentTypes.addAll(optionalPositionalArgumentTypes);
      optionalPositionalArgumentTypes.clear();
      requiredPositionalArgumentTypes.addAll(positionalVarargArgumentTypes);
      positionalVarargArgumentTypes.clear();
    }

    int actualArity = ContainerUtil.exists(requiredPositionalArgumentTypes, Conditions.instanceOf(PyVariadicType.class)) ?
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
        assert positionalVarargArgumentTypes.size() == 1 && positionalVarargArgumentTypes.get(0) instanceof PyVariadicType;
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

    NullTolerantDeque<PyType> expectedTypesDeque = new NullTolerantDeque<>(flattenUnpackedTupleTypes(expectedTypes));
    NullTolerantDeque<PyType> actualTypesDeque = new NullTolerantDeque<>(flattenUnpackedTupleTypes(actualTypes));

    List<Couple<PyType>> leftMappedTypes = new ArrayList<>();
    List<Couple<PyType>> centerMappedTypes = new ArrayList<>();
    List<Couple<PyType>> rightMappedTypes = new ArrayList<>();

    boolean splittingTypeVarTuple = false;
    while (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0) {
      PyType leftmostExpected = expectedTypesDeque.peekFirst();
      // Either a variadic type parameter *Ts or an unbounded unpacked tuple *tuple[int, ...] 
      if (leftmostExpected instanceof PyVariadicType) {
        break;
      }
      // The leftmost expected type is a regular type
      PyType leftmostActual = actualTypesDeque.peekFirst();
      if (leftmostActual instanceof PyVariadicType) {
        break;
      }
      expectedTypesDeque.removeFirst();
      actualTypesDeque.removeFirst();
      leftMappedTypes.add(Couple.of(leftmostExpected, leftmostActual));
    }

    while (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0) {
      PyType rightmostExpected = expectedTypesDeque.peekLast();
      if (rightmostExpected instanceof PyVariadicType) {
        break;
      }
      expectedTypesDeque.removeLast();
      PyType rightmostActual = actualTypesDeque.peekLast();
      if (rightmostActual instanceof PyVariadicType rightmostActualVariadic) {
        if (rightmostActualVariadic instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound()) {
          PyType repeatedActualType = unpackedTupleType.getElementTypes().get(0);
          rightMappedTypes.add(Couple.of(rightmostExpected, repeatedActualType));
        }
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

    if (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0
        && !(expectedTypesDeque.peekFirst() instanceof PyVariadicType)
        && (actualTypesDeque.peekFirst() instanceof PyVariadicType variadic)) {
      if (variadic instanceof PyUnpackedTupleType actualUnpackedTupleType && actualUnpackedTupleType.isUnbound()) {
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

    boolean sizeMismatch;
    if (expectedTypesDeque.size() == 0) {
      boolean allActualTypesMatched = actualTypesDeque.size() == 0;
      boolean onlySingleActualVariadicLeft = actualTypesDeque.size() == 1 &&
                                             actualTypesDeque.peekFirst() instanceof PyVariadicType;
      sizeMismatch = !(allActualTypesMatched || onlySingleActualVariadicLeft);
    }
    else if (expectedTypesDeque.size() == 1) {
      PyType onlyLeftExpectedType = expectedTypesDeque.peekFirst();
      if (onlyLeftExpectedType instanceof PyVariadicType) {
        if (actualTypesDeque.size() == 1 && actualTypesDeque.peekFirst() instanceof PyVariadicType variadicType) {
          centerMappedTypes.add(Couple.of(onlyLeftExpectedType, variadicType));
        }
        else {
          List<PyType> unmatchedActualTypes = actualTypesDeque.toList();
          if (optionSet.contains(Option.USE_DEFAULTS)
              && onlyLeftExpectedType instanceof PyTypeVarTupleType typeVarTupleType
              && typeVarTupleType.getDefaultType() != null
              && unmatchedActualTypes.isEmpty()) {
            centerMappedTypes.add(Couple.of(onlyLeftExpectedType, typeVarTupleType.getDefaultType()));
          }
          else {
            centerMappedTypes.add(Couple.of(onlyLeftExpectedType, PyUnpackedTupleTypeImpl.create(unmatchedActualTypes)));
          }
        }
        sizeMismatch = false;
      }
      else {
        Couple<PyType> fallbackMapping = mapToFallback(onlyLeftExpectedType, optionSet);
        ContainerUtil.addIfNotNull(centerMappedTypes, fallbackMapping);
        sizeMismatch = fallbackMapping == null;
      }
    }
    else {
      sizeMismatch = true;
      for (PyType unmatchedType : expectedTypesDeque.toList()) {
        Couple<PyType> fallbackMapping = mapToFallback(unmatchedType, optionSet);
        ContainerUtil.addIfNotNull(centerMappedTypes, fallbackMapping);
        sizeMismatch = fallbackMapping == null;
      }
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

  private static @Nullable Couple<PyType> mapToFallback(@Nullable PyType unmatchedExpectedType, @NotNull EnumSet<Option> optionSet) {
    if (optionSet.contains(Option.USE_DEFAULTS) &&
        unmatchedExpectedType instanceof PyTypeParameterType typeParameterType &&
        typeParameterType.getDefaultType() != null) {
      return Couple.of(unmatchedExpectedType, typeParameterType.getDefaultType());
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
}
