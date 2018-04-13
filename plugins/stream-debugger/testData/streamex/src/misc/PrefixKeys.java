package misc;

import one.util.streamex.EntryStream;

public class PrefixKeys {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9).prefixKeys((l, r) -> l + r).forEach(x -> {});
  }
}
