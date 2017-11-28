package concatenate;

import one.util.streamex.StreamEx;

public class PrependToEmpty {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.empty().prepend(1).count();
    System.out.println(count);
  }
}
