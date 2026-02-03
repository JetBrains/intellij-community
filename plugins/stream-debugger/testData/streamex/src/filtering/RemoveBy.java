package filtering;

import one.util.streamex.StreamEx;

public class RemoveBy {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 3, 4).removeBy(x -> x * x, 16).forEach(System.out::print);
  }
}
