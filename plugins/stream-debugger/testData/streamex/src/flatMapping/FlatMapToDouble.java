package flatMapping;

import one.util.streamex.DoubleStreamEx;
import one.util.streamex.IntStreamEx;

public class FlatMapToDouble {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = IntStreamEx.of(1, 2, 3).flatMapToDouble(x -> DoubleStreamEx.of(x, x, x)).count();
    System.out.println(count);
  }
}
