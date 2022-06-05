import java.util.stream.Stream;

public class MapMultiPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final double[] result = Stream.of(1, 5, 9).mapMultiToDouble((x, sink) -> {
      sink.accept(x + 1);
      sink.accept(x + 2);
      sink.accept(x + 3);
    }).toArray();
  }
}
