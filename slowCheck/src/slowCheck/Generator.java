package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

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

  public static <T> Generator<T> oneOf(T... values) {
    return oneOf(Arrays.asList(values));
  }

  public static <T> Generator<T> oneOf(List<T> values) {
    if (values.isEmpty()) throw new IllegalArgumentException("No items to choose from");
    return GenNumber.integers(0, values.size() - 1).map(values::get).noShrink();
  }
}
