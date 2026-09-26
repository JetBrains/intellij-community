package distinct;

import one.util.streamex.StreamEx;

public class DistinctWithStatefulExtractor {
  public static void main(String[] args) {
    final int[] state = new int[]{0};
    // Breakpoint! lambdaOrdinal(-1)
    StreamEx.of(3, 2, 1, 1, 1, 1).distinct(x -> x + state[0]++).count();
  }
}
