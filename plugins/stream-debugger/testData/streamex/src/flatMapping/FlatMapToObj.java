package flatMapping;

import one.util.streamex.LongStreamEx;
import one.util.streamex.StreamEx;

public class FlatMapToObj {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = LongStreamEx.of(1, 2, 3).flatMapToObj(x -> StreamEx.of(x, x + 1)).count();
    System.out.println(count);
  }
}
