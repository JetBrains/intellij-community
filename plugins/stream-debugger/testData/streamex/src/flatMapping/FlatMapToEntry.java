package flatMapping;

import one.util.streamex.StreamEx;

import java.util.Collections;
import java.util.Map;

public class FlatMapToEntry {
  private static Map<Object, Double> toMap(Object obj) {
    return Collections.singletonMap(obj, 2.);
  }

  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3).flatMapToEntry(FlatMapToEntry::toMap).count();
    System.out.println(count);
  }
}
