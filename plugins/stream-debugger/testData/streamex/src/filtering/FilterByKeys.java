package filtering;

import one.util.streamex.EntryStream;

public class FilterByKeys {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).filterKeys(x -> x < 2).count();
    System.out.println(count);
  }
}
