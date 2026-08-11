import java.util.stream.IntStream;

public class MultiLineChain {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    IntStream.of(1, 2, 3)
      .map(x -> x + 1)
      .filter(x -> x > 0)
      .sum();
  }
}
