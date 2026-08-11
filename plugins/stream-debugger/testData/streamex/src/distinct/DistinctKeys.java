package distinct;

import one.util.streamex.EntryStream;

public class DistinctKeys {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9, 3, 8).distinctKeys().count();
    System.out.println(count);
  }
}
