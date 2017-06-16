package filtering;

import one.util.streamex.EntryStream;

public class RemoveKeys {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).removeKeys(x -> x < 7).count();
    System.out.println(count);
  }
}
