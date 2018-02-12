package partialReduction;

import one.util.streamex.EntryStream;

import java.util.stream.Collectors;

public class CollapseKeys {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = EntryStream.of(1, 1, 2, 4, 3, 9).collapseKeys(Collectors.toList()).count();
    System.out.println(count);
  }
}
