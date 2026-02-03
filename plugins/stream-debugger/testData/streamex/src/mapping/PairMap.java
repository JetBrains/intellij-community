package mapping;

import one.util.streamex.IntStreamEx;

public class PairMap {
  public static void main(String[] args) {
    // Breakpoint!
    final int sum = IntStreamEx.of(1, 3, 4).pairMap((l, r) -> l + r).sum();
    System.out.println(sum);
  }
}
