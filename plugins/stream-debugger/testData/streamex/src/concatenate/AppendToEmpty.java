package concatenate;

import one.util.streamex.StreamEx;

public class AppendToEmpty {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.empty().append(1).count();
    System.out.println(count);
  }
}
