package sorted;

import one.util.streamex.StreamEx;

public class SortedByInt {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(2, 1).sortedByInt(Integer::intValue).forEach(System.out::println);
  }
}
