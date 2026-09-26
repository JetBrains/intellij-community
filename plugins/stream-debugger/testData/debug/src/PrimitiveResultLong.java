import java.util.stream.LongStream;

public class PrimitiveResultLong {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    long res = LongStream.of(1, 2).sum();
  }
}
