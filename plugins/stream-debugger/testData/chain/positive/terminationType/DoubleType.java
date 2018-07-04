import java.util.stream.DoubleStream;

public class Baz {
  public static void bar() {
<caret>    DoubleStream.of(1, 2, 3).sum();
  }
}
