package filtering;

import one.util.streamex.IntStreamEx;

public class AtLeast {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final int result = IntStreamEx.of(1, 2, 3, 4).atLeast(3).sum();
    System.out.println(result);
  }
}
