package filtering;

import one.util.streamex.StreamEx;

public class Remove {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 3, 4).remove(x -> x % 3 == 0).forEach(System.out::print);
  }
}
