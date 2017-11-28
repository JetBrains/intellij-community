package peek;

import one.util.streamex.StreamEx;

public class Peek {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3)
        .peekFirst(System.out::print)
        .peekLast(System.out::print)
        .mapToEntry(x -> x * x)
        .peekKeys(System.out::print)
        .peekValues(System.out::print)
        .peekKeyValue((k, v) -> System.out.println("(" + k + " -> " + v + ")"))
        .count();
    System.out.println(count);
  }
}
