import java.util.stream.Stream;

public class EvaluationExceptionDetected {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1, 2, 3).forEach(x -> {
    });
  }
}
