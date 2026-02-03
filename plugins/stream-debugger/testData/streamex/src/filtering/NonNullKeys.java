package filtering;

import one.util.streamex.EntryStream;

public class NonNullKeys {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, null, 9).nonNullKeys().count();
    System.out.println(count);
  }
}
