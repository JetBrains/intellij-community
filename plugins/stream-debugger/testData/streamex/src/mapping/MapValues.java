package mapping;

import one.util.streamex.EntryStream;

public class MapValues {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9)
        .mapValues(x -> 0)
        .count();
    System.out.println(count);
  }
}
