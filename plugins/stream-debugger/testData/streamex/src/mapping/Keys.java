package mapping;

import one.util.streamex.EntryStream;

public class Keys {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9).keys().forEach(x -> {});
  }
}
