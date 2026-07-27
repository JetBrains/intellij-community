import java.util.stream.Stream;

public class MapMultiEmpty {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long res = Stream.of(1, 2, 3).<Integer>mapMulti((x, c) -> {
      // Process every element, but never push anything downstream
    }).count();
  }
}
