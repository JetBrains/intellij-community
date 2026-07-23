import java.util.stream.Stream;

public class MapMultiToInt {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final int[] result = Stream.of(1, 5, 9).mapMultiToInt((x, c) -> {
      c.accept(x + 1);
      c.accept(x + 2);
    }).toArray();
  }
}
