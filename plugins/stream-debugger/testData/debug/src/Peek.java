import java.util.stream.Stream;

public class Peek {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = Stream.of(1, 2, 3).peek(x -> {}).count();
  }
}
