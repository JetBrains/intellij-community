import java.util.stream.IntStream;

public class PrimitiveResultInt {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    int res = IntStream.of(1, 2).sum();
  }
}
