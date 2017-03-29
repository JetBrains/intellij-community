import java.util.stream.Stream;

public class ExceptionWhenEvaluating {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = Stream.of(1, 2).peek(x -> {
      throw new RuntimeException("error!");
    }).count();
  }
}