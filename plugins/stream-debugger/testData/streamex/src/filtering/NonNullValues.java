package filtering;

import one.util.streamex.EntryStream;

public class NonNullValues {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, null, 3, null).nonNullKeys().count();
    System.out.println(count);
  }
}
