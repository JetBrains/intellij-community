package mapping;

import one.util.streamex.StreamEx;

public class MapFirstOrElse {
  public static void main(String[] args) {
    // Breakpoint!
    final int max = StreamEx.of(1, 2, 3).mapFirstOrElse(x -> 10, x -> 20).max(Integer::compareTo).get();
    System.out.println(max);
  }
}
