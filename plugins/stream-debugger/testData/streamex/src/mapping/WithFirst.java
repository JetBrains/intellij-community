package mapping;

import one.util.streamex.StreamEx;

public class WithFirst {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(10., 2., 3.).withFirst((first, other) -> first * other).count();
    System.out.println(count);
  }
}
