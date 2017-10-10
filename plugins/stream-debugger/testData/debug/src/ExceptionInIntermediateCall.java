import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExceptionInIntermediateCall {

  public static void main(String[] args) {
    try {
      check();
    } catch (Throwable ignored) {
    }
  }

  private static void check() {
    // Breakpoint!
    final List<Integer> res = Stream.of(1, 2, 3).map(x -> {
      if (x % 2 == 1) {
        return x;
      }
      throw new RuntimeException();
    }).collect(Collectors.toList());
  }
}
