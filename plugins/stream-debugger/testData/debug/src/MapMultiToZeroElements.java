import java.util.stream.Stream;

public class MapMultiToZeroElements {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = Stream.of(1, 2, 3, 4).mapMulti((x, sink) -> {
      if (x % 2 == 0) {
        sink.accept(x + 1);
        sink.accept(x + 2);
      }
    }).count(); // 4
  }
}
