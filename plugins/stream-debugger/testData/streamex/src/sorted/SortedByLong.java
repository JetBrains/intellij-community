package sorted;

import one.util.streamex.StreamEx;

public class SortedByLong {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 10).sortedByLong(x -> 10 - x).forEach(System.out::println);
  }
}
