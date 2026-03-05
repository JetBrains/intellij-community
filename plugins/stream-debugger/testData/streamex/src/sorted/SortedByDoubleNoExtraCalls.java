package sorted;

import one.util.streamex.StreamEx;

public class SortedByDoubleNoExtraCalls {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(10, 1, 2).sortedByDouble(Integer::doubleValue).forEach(System.out::println);
  }
}
