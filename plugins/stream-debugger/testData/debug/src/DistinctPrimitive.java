import java.util.stream.LongStream;

public class DistinctPrimitive {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long res = LongStream.of(1, 1, 1).distinct().count();
  }
}
