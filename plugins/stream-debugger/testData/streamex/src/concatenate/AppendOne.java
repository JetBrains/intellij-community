package concatenate;

import one.util.streamex.StreamEx;

public class AppendOne {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    StreamEx.of(1, 2).append(3).forEach(x -> {});
  }
}
