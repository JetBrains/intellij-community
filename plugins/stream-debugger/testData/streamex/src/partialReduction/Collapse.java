package partialReduction;

import one.util.streamex.StreamEx;

public class Collapse {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 3, 5, 4, 2, 3).collapse((x, y) -> x % 2 == y % 2, (x, y) -> x + y).forEach(System.out::println);
  }
}
