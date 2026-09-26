import java.util.stream.IntStream;

public class ArgEvalBetweenCalls {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    combine(IntStream.of(1, 2).sum(), IntStream.of(3, 4).sum());
  }

  private static int combine(int a, int b) {
    return a + b;
  }
}
