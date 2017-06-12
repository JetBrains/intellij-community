package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
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
  
  public <V> Generator<V> map(Function<T,V> fun) {
    return from(data -> fun.apply(generateUnstructured(data)));
  }
  
  public Generator<T> noShrink() {
    return from(data -> data.generateNonShrinkable(this));
  }

  public Generator<T> suchThat(Predicate<T> condition) {
    return from(data -> data.generateConditional(this, condition));
  }

  public static Generator<Integer> integers() {
    return from(data -> data.drawInt());
  }

  public static Generator<Integer> integers(int min, int max) {
    IntDistribution distribution = IntDistribution.uniform(min, max);
    return from(data -> data.drawInt(distribution));
  }

  public static Generator<Double> doubles() {
    return from(new DoubleGenerator());
  }

  public static <T> Generator<List<T>> listOf(Generator<T> itemGenerator) {
    return listOf(IntDistribution.uniform(0, 42), itemGenerator);
  }

  public static <T> Generator<List<T>> listOf(IntDistribution length, Generator<T> itemGenerator) {
    return from(data -> {
      int size = data.drawInt(length);
      List<T> list = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        list.add(itemGenerator.generateValue(data));
      }
      return Collections.unmodifiableList(list);
    });
  }

  public static <T> Generator<T> oneOf(T... values) {
    return oneOf(Arrays.asList(values));
  }

  public static <T> Generator<T> oneOf(List<T> values) {
    if (values.isEmpty()) throw new IllegalArgumentException("No items to choose from");
    return integers(0, values.size() - 1).map(values::get).noShrink();
  }

  public static Generator<Character> characterRange(char min, char max) {
    return integers(min, max).map(i -> (char)i.intValue()).noShrink();
  }

  public static Generator<Character> asciiPrintableChar() {
    return characterRange((char)32, (char)126);
  }

  public static Generator<Character> asciiUppercaseChar() {
    return characterRange('A', 'Z');
  }

  public static Generator<Character> asciiLowercaseChar() {
    return characterRange('a', 'z');
  }

  public static Generator<Character> asciiLetterChar() {
    return from(new Frequency<Character>()
      .withAlternative(9, asciiLowercaseChar())
      .withAlternative(1, asciiUppercaseChar()))
      .noShrink();
  }

  public static Generator<String> stringOf(Generator<Character> charGen) {
    return listOf(charGen).map(chars -> {
      StringBuilder sb = new StringBuilder();
      chars.forEach(sb::append);
      return sb.toString();
    });
  }

}
