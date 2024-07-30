import java.util.stream.Stream;

public class DropWhile {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = Stream.of(1, 2, 3, 4, 5, 6, 7, 8).dropWhile(x -> x < 5).count(); // 4
  }
}