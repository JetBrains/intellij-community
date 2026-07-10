import java.util.stream.DoubleStream;

public class PrimitiveNoneMatchMapping {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final boolean res = DoubleStream.of(1., 4.).noneMatch(x -> x > 0);
  }
}