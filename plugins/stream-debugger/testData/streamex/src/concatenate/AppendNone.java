package concatenate;

import one.util.streamex.StreamEx;

public class AppendNone {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2).append().forEach(x -> {});
  }
}
