package distinct;

import one.util.streamex.StreamEx;

public class DistinctWithKeyExtractor {
  private static int hash(int x) {
    return x % 3;
  }

  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 4, 5, 6, 7).distinct(DistinctWithKeyExtractor::hash).count();
    System.out.println(count);
  }
}
