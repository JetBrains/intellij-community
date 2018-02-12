package mapping;

import one.util.streamex.IntStreamEx;

public class MapLast {
  public static void main(String[] args) {
    // Breakpoint!
    final int sum = IntStreamEx.of(1, 2, 3).mapLast(x -> 30).sum();
    System.out.println(sum);
  }
}
