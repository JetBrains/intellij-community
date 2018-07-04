package misc;

import one.util.streamex.StreamEx;

public class HeadTail {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 4).headTail((head, tail) -> tail, () -> StreamEx.of(0)).count();
    System.out.println(count);
  }
}
