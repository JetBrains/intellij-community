package flatMapping;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

public class FlatMapValues {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).flatMapValues(v -> StreamEx.of(v, v + 1)).count();
    System.out.println(count);
  }
}
