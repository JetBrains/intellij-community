import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    IntStream.of(1, 2, 3).sum();
  }
}
