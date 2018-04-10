package mapping;

import one.util.streamex.EntryStream;

public class Join {
  public static void main(String[] args) {
    // Breakpoint!
    EntryStream.of(1, 1, 2, 4, 3, 9).join(" -> ").forEach(System.out::println);
  }
}
