package concatenate;

import one.util.streamex.StreamEx;

public class PrependToEmpty {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.empty().prepend(1).forEach(x -> {});
  }
}
