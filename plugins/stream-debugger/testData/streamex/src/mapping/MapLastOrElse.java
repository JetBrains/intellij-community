package mapping;

import one.util.streamex.StreamEx;

import java.util.Optional;

public class MapLastOrElse {
  public static void main(String[] args) {
    // Breakpoint!
    final Optional<Integer> min = StreamEx.of(1, 2, 3).mapLastOrElse(x -> 30, x -> x - 1).min(Integer::compareTo);
    System.out.println(min.get().intValue());
  }
}
