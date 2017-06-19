package flatMapping;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

public class FlatMapToValue {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9)
        .flatMapToValue((k, v) -> StreamEx.of(k, k + 1))
        .count();
    System.out.println(count);
  }
}
