package misc;

import one.util.streamex.IntStreamEx;

public class SkipOrdered {
  public static void main(String[] args) {
    // Breakpoint!
    final int sum = IntStreamEx.of(1, 2).skipOrdered(1).sum();
    System.out.println(sum);
  }
}
