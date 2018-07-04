import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ExceptionInTerminatorCall {
  public static void main(String[] args) {
    try {
      check();
    } catch (Throwable ignored) {
    }
  }

  private static void check() {
    // Breakpoint!
    final Integer ex = Stream.of(1, 2, 3).reduce(0, (l, r) -> {
      throw new RuntimeException();
    });
  }
}
