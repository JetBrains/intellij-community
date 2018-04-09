package mapping;

import one.util.streamex.EntryStream;

public class MapToKey {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9)
        .mapToKey((k, v) -> k + v)
        .forEach(x -> {});
  }
}
