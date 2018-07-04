import java.util.stream.Stream;

public class Skip {
  public static void main(String[] args) {
    // Breakpoint!
    final long res = Stream.of(1, 2, 3).skip(2).count();
  }
}
