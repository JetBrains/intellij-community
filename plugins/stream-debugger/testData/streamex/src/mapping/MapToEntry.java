package mapping;

import one.util.streamex.StreamEx;

public class MapToEntry {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3).mapToEntry(x -> x * x).count();
    System.out.println(count);
  }
}
