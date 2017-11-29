import java.util.stream.DoubleStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final long res = DoubleStream.of(1.,2.,3.).boxed().count();
  }
}
