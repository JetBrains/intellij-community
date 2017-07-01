package flatMapping;

import one.util.streamex.StreamEx;

public class FlatArray {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3).flatArray(x -> new Integer[]{x + 1, x + 2, x + 3}).count();
    System.out.println(count);
  }
}
