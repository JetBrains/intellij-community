package misc;

import one.util.streamex.EntryStream;

public class PrefixValues {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9).prefixValues((l, r) -> l + r).forEach(x -> {});
  }
}
