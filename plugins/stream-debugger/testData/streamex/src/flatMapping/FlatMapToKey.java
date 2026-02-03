package flatMapping;

import one.util.streamex.EntryStream;

import java.util.stream.Stream;

public class FlatMapToKey {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).flatMapToKey((k, v) -> Stream.of(k, k + 1)).count();
    System.out.println(count);
  }
}
