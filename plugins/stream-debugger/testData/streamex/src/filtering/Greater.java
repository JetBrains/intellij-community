package filtering;

import one.util.streamex.DoubleStreamEx;

import java.util.OptionalDouble;

public class Greater {
  public static void main(String[] args) {
    // Breakpoint!
    final OptionalDouble result = DoubleStreamEx.of(1., 2., 3., 4.).greater(2).min();
    System.out.println(result.orElse(-1.));
  }
}
