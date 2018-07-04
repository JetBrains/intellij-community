package distinct;

import one.util.streamex.EntryStream;

public class DistinctValues {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9, 0, 9).distinctValues().count();
    System.out.println(count);
  }
}
