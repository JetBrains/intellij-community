package concatenate;

import one.util.streamex.StreamEx;

public class AppendToEmpty {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.empty().append(1).forEach(x -> {});
  }
}
