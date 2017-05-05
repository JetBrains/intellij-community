import java.util.stream.DoubleStream;

public class PrimitiveNoneMatchMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final boolean res = DoubleStream.of(1., 4.).noneMatch(x -> x > 0);
  }
}