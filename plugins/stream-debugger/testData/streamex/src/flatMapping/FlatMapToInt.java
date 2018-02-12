package flatMapping;

import one.util.streamex.DoubleStreamEx;

import java.util.stream.IntStream;

public class FlatMapToInt {
  public static void main(String[] args) {
    // Breakpoint!
    final int sum = DoubleStreamEx.of(1, 2, 3).flatMapToInt(x -> IntStream.of(1, 3)).sum();
    System.out.println(sum);
  }
}
