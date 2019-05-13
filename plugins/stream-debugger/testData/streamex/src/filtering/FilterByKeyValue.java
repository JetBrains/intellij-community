package filtering;

import one.util.streamex.EntryStream;

public class FilterByKeyValue {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).filterKeyValue((k, v) -> k * v < 10).count();
    System.out.println(count);
  }
}
