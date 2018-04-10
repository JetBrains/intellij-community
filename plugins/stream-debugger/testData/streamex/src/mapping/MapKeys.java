package mapping;

import one.util.streamex.EntryStream;

public class MapKeys {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9).mapKeys(x -> x - 1).forEach(x -> {});
  }
}
