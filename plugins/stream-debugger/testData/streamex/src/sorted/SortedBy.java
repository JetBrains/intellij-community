package sorted;

import one.util.streamex.StreamEx;

public class SortedBy {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 10).sortedBy(Object::toString).forEach(System.out::println);
  }
}
