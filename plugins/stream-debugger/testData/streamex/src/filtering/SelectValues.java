package filtering;

import one.util.streamex.EntryStream;

public class SelectValues {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4L, 3, new Object()).selectValues(Long.class).count();
    System.out.println(count);
  }
}
