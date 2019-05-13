package filtering;

import one.util.streamex.LongStreamEx;

import java.util.OptionalLong;

public class AtMost {
  public static void main(String[] args) {
    // Breakpoint!
    final OptionalLong result = LongStreamEx.of(1, 2, 3, 4).atMost(2).min();
    System.out.println(result.orElse(-1));
  }
}
