package slowCheck;

import java.util.*;

/**
 * @author peter
 */
public class GenCollection {
  public static <T> Generator<List<T>> listOf(Generator<T> itemGenerator) {
    return Generator.from(data -> generateList(itemGenerator, data, data.suggestCollectionSize()));
  }

  public static <T> Generator<List<T>> nonEmptyListOf(Generator<T> itemGenerator) {
    return listOf(itemGenerator).suchThat(l -> !l.isEmpty());
  }

  public static <T> Generator<List<T>> listOf(IntDistribution length, Generator<T> itemGenerator) {
    return Generator.from(data -> generateList(itemGenerator, data, data.drawInt(length)));
  }

  private static <T> List<T> generateList(Generator<T> itemGenerator, DataStructure data, int size) {
    List<T> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(itemGenerator.generateValue(data));
    }
    return Collections.unmodifiableList(list);
  }
}
