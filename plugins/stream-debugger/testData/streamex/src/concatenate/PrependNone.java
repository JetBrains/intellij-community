package concatenate;

import one.util.streamex.StreamEx;

public class PrependNone {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2).prepend().count();
    System.out.println(count);
  }
}
