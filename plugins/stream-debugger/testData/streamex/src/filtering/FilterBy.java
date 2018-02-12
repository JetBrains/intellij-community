package filtering;

import one.util.streamex.StreamEx;

public class FilterBy {
  public static void main(String[] args) {
    // Breakpoint!
    final Object[] result = StreamEx.of(1, 2, 3, 4).filterBy(x -> x * x, 9).toArray();
    System.out.println(result[0]);
  }
}
