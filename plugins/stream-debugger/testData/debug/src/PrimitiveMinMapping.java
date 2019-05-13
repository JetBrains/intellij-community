import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

public class PrimitiveMinMapping {
  public static void main(String[] args) {
    // Breakpoint!
    final OptionalDouble res = DoubleStream.of(1., 2., 10.).min();
  }
}