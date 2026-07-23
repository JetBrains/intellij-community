import java.util.stream.Stream;

public class MapMultiFilter {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long res = Stream.of(1, 2, 3, 4, 5).<Integer>mapMulti((x, c) -> {
      if (x % 2 == 0) {
        c.accept(x * 10);
      }
    }).count();
  }
}
