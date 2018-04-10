package mapping;

import one.util.streamex.EntryStream;

public class Values {
  public static void main(String[] args) {
    // Breakpoint!
    final long sum = EntryStream.of(1, 1, 2, 4, 3, 9)
        .values()
        .mapToInt(Integer::intValue)
        .sum();
    System.out.println(sum);
  }
}
