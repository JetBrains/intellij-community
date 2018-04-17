package concatenate;

import one.util.streamex.StreamEx;

public class PrependNone {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2).prepend().forEach(x -> {});
  }
}
