package slowCheck;

import java.util.Random;

/**
 * @author peter
 */
public interface IntDistribution {
  int generateInt(Random random);

  boolean isValidValue(int i);
  
  /**
   * This distribution returns an integer uniformly distributed between {@code min} and {@code max} (both ends inclusive).
   */
  static IntDistribution uniform(int min, int max) {
    return new BoundedIntDistribution(min, max, r -> {
      if (min == max) return min;
      
      int i = r.nextInt();
      return i >= min && i <= max ? i : Math.abs(i) % (max - min + 1) + min;
    });
  }

  /**
   * Geometric distribution with a given mean
   */
  static IntDistribution geometric(int mean) {
    double p = 1.0 / mean;
    return new IntDistribution() {
      @Override
      public int generateInt(Random random) {
        double u = random.nextDouble();
        return (int) (Math.log(u) / Math.log(1 - p));
      }

      @Override
      public boolean isValidValue(int i) {
        return i >= 0;
      }
    };
  }

  /**
   * This distribution returns 0 or 1, where 1 will be returned with the given probability
   */
  static IntDistribution biasedCoin(double probabilityOfOne) {
    return new BoundedIntDistribution(0, 1, r -> r.nextDouble() < probabilityOfOne ? 1 : 0);
  }
}
