import java.util.stream.Stream;

public class FlatMap {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = Stream.of(1, 4).flatMap(x -> Stream.of(x + 1, x + 2)).count();
  }
}
