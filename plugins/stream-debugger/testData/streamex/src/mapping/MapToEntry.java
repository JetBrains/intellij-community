package mapping;

import one.util.streamex.StreamEx;

public class MapToEntry {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = StreamEx.of(1, 2, 3).mapToEntry(x -> x * x).toArray();
    System.out.println(result.length);
  }
}
