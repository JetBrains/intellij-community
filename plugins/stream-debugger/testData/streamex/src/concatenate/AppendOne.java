package concatenate;

import one.util.streamex.StreamEx;

public class AppendOne {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2).append(3).count();
    System.out.println(count);
  }
}
