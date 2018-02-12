import java.util.stream.IntStream;

public class ExceptionInStreamWithPrimitiveResult {
  public static void main(String[] args) {
    try {
      check();
    } catch (Throwable ignored) {
    }
  }

  private static void check() {
    // Breakpoint!
    final int sum = IntStream.of(1, 2, 3, 4).peek(x -> {
      throw new RuntimeException();
    }).reduce(0, (l, r) -> l + r);
  }
}
