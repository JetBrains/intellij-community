package slowCheck;

/**
 * @author peter
 */
public class GenNumber {
  public static Generator<Integer> integers() {
    return Generator.from(data -> data.drawInt());
  }

  public static Generator<Integer> integers(int min, int max) {
    IntDistribution distribution = IntDistribution.uniform(min, max);
    return Generator.from(data -> data.drawInt(distribution));
  }

  public static Generator<Double> doubles() {
    return Generator.from(new DoubleGenerator());
  }
}
