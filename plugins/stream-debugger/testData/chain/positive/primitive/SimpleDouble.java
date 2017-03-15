import java.util.stream.DoubleStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final double res = DoubleStream.of(1.).sum();
  }
}
