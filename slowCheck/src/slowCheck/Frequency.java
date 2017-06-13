package slowCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author peter
 */
class Frequency<T> implements Function<DataStructure, T> {
  private List<Integer> frequencies = new ArrayList<>();
  private List<Generator<T>> outcomes = new ArrayList<>();

  Frequency<T> withAlternative(int frequency, Generator<T> gen) {
    frequencies.add(frequency);
    outcomes.add(gen);
    return this;
  }

  @Override
  public T apply(DataStructure data) {
    int index = data.drawInt(frequencyDistribution());
    return outcomes.get(index).generateValue(data);
  }

  private IntDistribution frequencyDistribution() {
    int sum = frequencies.stream().reduce(0, (a, b) -> a + b);
    return new BoundedIntDistribution(0, frequencies.size() - 1, r -> {
      int value = r.nextInt(sum);
      for (int i = 0; i < frequencies.size(); i++) {
        value -= frequencies.get(i);
        if (value <= 0) return i;
      }
      throw new IllegalArgumentException();
    });
  }
}
