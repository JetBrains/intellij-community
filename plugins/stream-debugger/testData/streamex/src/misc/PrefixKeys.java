package misc;

import one.util.streamex.EntryStream;

public class PrefixKeys {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).prefixKeys((l, r) -> l + r).count();
    System.out.println(count);
  }
}
