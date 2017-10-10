package misc;

import one.util.streamex.StreamEx;

import java.util.stream.Stream;

public class Chain {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 4).chain(x -> dividable(x, 3)).count();
    System.out.println(count);
  }

  private static Stream<Integer> dividable(Stream<Integer> stream, int num) {
    return stream.filter(x -> x % num == 0);
  }
}
