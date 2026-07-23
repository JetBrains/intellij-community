import java.util.stream.Stream;

public class MapMultiToLong {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long[] result = Stream.of(1, 5, 9).mapMultiToLong((x, c) -> {
      c.accept(x + 1);
      c.accept(x + 2);
    }).toArray();
  }
}
