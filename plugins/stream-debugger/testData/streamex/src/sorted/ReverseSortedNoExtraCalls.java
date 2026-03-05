package sorted;

import one.util.streamex.StreamEx;

public class ReverseSortedNoExtraCalls {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1,2,3).reverseSorted().forEach(System.out::println);
  }
}
