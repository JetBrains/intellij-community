import java.util.stream.Stream;

public class MapMulti {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long res = Stream.of(1, 4).<Integer>mapMulti((x, c) -> {
      c.accept(x + 1);
      c.accept(x + 2);
    }).count();
  }
}
