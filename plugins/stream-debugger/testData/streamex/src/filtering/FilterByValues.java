package filtering;

import one.util.streamex.EntryStream;

public class FilterByValues {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).filterValues(x -> x < 5).count();
    System.out.println(count);
  }
}
