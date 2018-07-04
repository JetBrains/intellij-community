package flatMapping;

import one.util.streamex.StreamEx;

public class Cross {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3).cross(1, 2, 3).count();
    System.out.println(count);
  }
}
