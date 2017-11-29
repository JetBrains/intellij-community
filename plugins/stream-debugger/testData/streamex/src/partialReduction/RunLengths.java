package partialReduction;

import one.util.streamex.StreamEx;

public class RunLengths {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 2, 3, 2, 2, 4).runLengths().count();
    System.out.println(count);
  }
}
