import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    IntStream.of(1, 3).anyMatch(x -> x % 2 == 0);
  }
}
