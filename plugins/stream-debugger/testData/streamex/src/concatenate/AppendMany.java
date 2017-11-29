package concatenate;

import one.util.streamex.StreamEx;

import java.util.stream.Stream;

public class AppendMany {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2).append(Stream.of(3, 4, 5)).count();
    System.out.println(count);
  }
}
