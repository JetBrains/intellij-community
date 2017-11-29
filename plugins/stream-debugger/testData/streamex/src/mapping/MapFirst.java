package mapping;

import one.util.streamex.IntStreamEx;

public class MapFirst {
  public static void main(String[] args) {
    // Breakpoint!
    final int sum = IntStreamEx.of(1, 2, 3).mapFirst(x -> 10).sum();
    System.out.println(sum);
  }
}
