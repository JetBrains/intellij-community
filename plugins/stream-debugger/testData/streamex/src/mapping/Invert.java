package mapping;

import one.util.streamex.EntryStream;

public class Invert {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9).invert().forEach(x -> {});
  }
}
