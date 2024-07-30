import java.util.stream.Stream;

public class MapMulti {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = Stream.of(1, 4).mapMulti((x, sink) -> {
      sink.accept(x + 1);
      sink.accept(x + 2);
    }).count(); // 4
  }
}
