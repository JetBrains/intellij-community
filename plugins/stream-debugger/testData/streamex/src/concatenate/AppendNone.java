package concatenate;

import one.util.streamex.StreamEx;

public class AppendNone {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2).append().count();
    System.out.println(count);
  }
}
