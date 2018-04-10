package partialReduction;

import one.util.streamex.StreamEx;

public class GroupRuns {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 2, 3, 5, 7).groupRuns((l, r) -> l % 2 == r % 2).count();
    System.out.println(count);
  }
}
