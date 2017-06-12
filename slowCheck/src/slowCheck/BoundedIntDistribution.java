package slowCheck;

import java.util.Random;
import java.util.function.ToIntFunction;

public final class BoundedIntDistribution implements IntDistribution {
  public static final IntDistribution ALL_INTS = IntDistribution.uniform(Integer.MIN_VALUE, Integer.MAX_VALUE);
  private final int min;
  private final int max;
  private final ToIntFunction<Random> producer;

  public BoundedIntDistribution(int min, int max, ToIntFunction<Random> producer) {
    if (min > max) throw new IllegalArgumentException(min + ">" + max);
    this.min = min;
    this.max = max;
    this.producer = producer;
  }

  @Override
  public int generateInt(Random random) {
    return producer.applyAsInt(random);
  }

  @Override
  public boolean isValidValue(int i) {
    return i >= min && i <= max;
  }
}