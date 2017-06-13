package slowCheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GenCollection {
  public static <T> Generator<List<T>> listOf(Generator<T> itemGenerator) {
    return listOf(IntDistribution.geometric(42), itemGenerator);
  }

  public static <T> Generator<List<T>> nonEmptyListOf(Generator<T> itemGenerator) {
    return listOf(BoundedIntDistribution.bound(1, Integer.MAX_VALUE, IntDistribution.geometric(42)), itemGenerator);
  }

  public static <T> Generator<List<T>> listOf(IntDistribution length, Generator<T> itemGenerator) {
    return Generator.from(data -> {
      int size = data.drawInt(length);
      List<T> list = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        list.add(itemGenerator.generateValue(data));
      }
      return Collections.unmodifiableList(list);
    });
  }
}
