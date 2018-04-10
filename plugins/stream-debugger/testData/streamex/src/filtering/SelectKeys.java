package filtering;

import one.util.streamex.EntryStream;

public class SelectKeys {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2L, 4, new Object(), 9).selectKeys(Long.class).count();
    System.out.println(count);
  }
}
