package filtering;

import one.util.streamex.IntStreamEx;

public class TakeWhile {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = IntStreamEx.of(1, 2, 3).takeWhile(x -> x % 2 != 0).count();
    System.out.println(count);
  }
}
