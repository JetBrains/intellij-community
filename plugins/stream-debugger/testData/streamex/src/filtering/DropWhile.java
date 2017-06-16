package filtering;

import one.util.streamex.StreamEx;

public class DropWhile {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 4).dropWhile(x -> x < 4).count();
    System.out.println(count);
  }
}
