package misc;

import one.util.streamex.StreamEx;

public class Prefix {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 3, 4, 5, 7).prefix((l, r) -> l + r).forEach(System.out::println);
  }
}
