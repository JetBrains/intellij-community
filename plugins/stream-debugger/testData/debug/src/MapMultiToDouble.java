import java.util.stream.Stream;

public class MapMultiToDouble {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final double[] result = Stream.of(1, 5, 9).mapMultiToDouble((x, c) -> {
      c.accept(x + 1);
      c.accept(x + 2);
    }).toArray();
  }
}
