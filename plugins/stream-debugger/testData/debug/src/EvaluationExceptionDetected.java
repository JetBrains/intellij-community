import java.util.stream.Stream;

public class EvaluationExceptionDetected {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(1, 2, 3).forEach(x -> {
    });
  }
}
