package flatMapping;

import one.util.streamex.StreamEx;

import java.util.Arrays;

public class FlatCollection {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3)
        .flatCollection(x -> Arrays.asList(new Integer[]{x + 1, x + 2, x + 3}))
        .count();
    System.out.println(count);
  }
}
