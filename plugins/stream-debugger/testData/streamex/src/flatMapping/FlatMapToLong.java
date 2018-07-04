package flatMapping;

import one.util.streamex.DoubleStreamEx;
import one.util.streamex.LongStreamEx;

public class FlatMapToLong {
  public static void main(String[] args) {
    // Breakpoint!
    final long sum = DoubleStreamEx.of(1, 2, 3).flatMapToLong(x -> LongStreamEx.of((long) x, (long) (x + 1))).sum();
    System.out.println(sum);
  }
}
