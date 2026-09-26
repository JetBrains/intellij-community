package filtering;

import one.util.streamex.IntStreamEx;

public class Without {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long count = IntStreamEx.of(1, 2, 3).without(2, 3).count();
    System.out.println(count);
  }
}
