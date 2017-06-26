package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A generator for objects based on random data from {@link DataStructure}.
 *
 * @see GenNumber 
 * @see GenChar 
 * @see GenString 
 * @see GenCollection 
 * @author peter
 */
public final class Generator<T> {
  private final Function<DataStructure, T> myFunction;

  private Generator(Function<DataStructure, T> function) {
    myFunction = function;
  }

  @NotNull
  public static <T> Generator<T> from(@NotNull Function<DataStructure, T> function) {
    return new Generator<>(function);
  }

  public T generateValue(@NotNull DataStructure data) {
    return myFunction.apply(data.subStructure());
  }

  public T generateUnstructured(@NotNull DataStructure data) {
    return myFunction.apply(data);
  }
  
  public <V> Generator<V> map(@NotNull Function<T,V> fun) {
    return from(data -> fun.apply(generateUnstructured(data)));
  }
  
  public Generator<T> noShrink() {
    return from(data -> data.generateNonShrinkable(this));
  }

  public Generator<T> suchThat(@NotNull Predicate<T> condition) {
    return from(data -> data.generateConditional(this, condition));
  }

  public static <T> Generator<T> constant(T value) {
    return from(data -> value);
  }

  @SafeVarargs
  public static <T> Generator<T> anyValue(T... values) {
    return anyValue(Arrays.asList(values));
  }

  public static <T> Generator<T> anyValue(List<T> values) {
    return oneOf(values.stream().map(Generator::constant).collect(Collectors.toList()));
  }

  @SafeVarargs 
  public static <T> Generator<T> oneOf(Generator<? extends T>... alternatives) {
    return oneOf(Arrays.asList(alternatives));
  }

  public static <T> Generator<T> oneOf(List<Generator<? extends T>> alternatives) {
    if (alternatives.isEmpty()) throw new IllegalArgumentException("No alternatives to choose from");
    return from(data -> {
      int index = data.generateNonShrinkable(GenNumber.integers(0, alternatives.size() - 1));
      return alternatives.get(index).generateValue(data);
    });
  }
 
  public static <T> Generator<T> frequency(int weight1, Generator<? extends T> alternative1, 
                                           int weight2, Generator<? extends T> alternative2) {
    Map<Integer, Generator<? extends T>> alternatives = new HashMap<>();
    alternatives.put(weight1, alternative1);
    alternatives.put(weight2, alternative2);
    return frequency(alternatives);
  }

  public static <T> Generator<T> frequency(int weight1, Generator<? extends T> alternative1, 
                                           int weight2, Generator<? extends T> alternative2,
                                           int weight3, Generator<? extends T> alternative3) {
    Map<Integer, Generator<? extends T>> alternatives = new HashMap<>();
    alternatives.put(weight1, alternative1);
    alternatives.put(weight2, alternative2);
    alternatives.put(weight3, alternative3);
    return frequency(alternatives);
  }
  
  public static <T> Generator<T> frequency(Map<Integer, Generator<? extends T>> alternatives) {
    List<Integer> weights = new ArrayList<>(alternatives.keySet());
    IntDistribution distribution = IntDistribution.frequencyDistribution(weights);
    return from(data -> alternatives.get(weights.get(data.drawInt(distribution))).generateValue(data));
  }

}
