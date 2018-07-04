import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class Baz {
  public static void main(String[] args) {
<caret>    final double res = Stream.of(1,2,3).flatMapToDouble(x -> DoubleStream.of(1., 2.)).sum();
  }
}
