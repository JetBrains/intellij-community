package filtering;

import one.util.streamex.StreamEx;

public class DropWhile {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 2).dropWhile(x -> x < 3).count();
    System.out.println(count);
  }
}
