package mapping;

import one.util.streamex.IntStreamEx;

public class Elements {
  public static void main(String[] args) {
    final int[] array = new int[]{20, 30, 40, 50};
    // Breakpoint!
    final int sum = IntStreamEx.of(1, 3).elements(array).sum();
    System.out.println(sum);
  }
}
