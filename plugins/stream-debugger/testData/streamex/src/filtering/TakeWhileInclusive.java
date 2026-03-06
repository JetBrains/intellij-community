package filtering;

import one.util.streamex.IntStreamEx;

public class TakeWhileInclusive {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long count = IntStreamEx.of(1, 2, 3).takeWhileInclusive(x -> x % 2 != 0).count();
    System.out.println(count);
  }
}
