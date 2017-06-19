package distinct;

import one.util.streamex.StreamEx;

public class DistinctAtLeast {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 2, 3, 2).distinct(3).count();
    System.out.println(count);
  }
}
