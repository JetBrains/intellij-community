package misc;

import one.util.streamex.StreamEx;

public class Chain {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3, 4).chain(x -> dividable(x, 3)).count();
    System.out.println(count);
  }

  private static StreamEx<Integer> dividable(StreamEx<Integer> stream, int num) {
    return stream.filter(x -> x % num == 0);
  }
}
