package partialReduction;

import one.util.streamex.StreamEx;

public class IntervalMap {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 4, 6, 7, 9, 10).intervalMap((l, r) -> l % 2 == r % 2, (l, r) -> l + r).forEach(System.out::println);
  }
}
