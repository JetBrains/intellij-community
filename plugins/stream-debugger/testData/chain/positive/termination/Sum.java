import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    int res = IntStream.of(1).sum();
  }
}
