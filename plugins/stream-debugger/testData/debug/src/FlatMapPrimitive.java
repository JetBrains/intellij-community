import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class FlatMapPrimitive {
  public static void main(String[] args) {
    // Breakpoint!
    final double[] result = Stream.of(1, 5, 9).flatMapToDouble(x -> DoubleStream.of(x + 1, x + 2, x + 3)).toArray();
  }
}
