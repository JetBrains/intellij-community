package concatenate;

import one.util.streamex.StreamEx;

public class PrependOne {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2).prepend(3).forEach(x -> {});
  }
}
