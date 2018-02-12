import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    final int[] res = IntStream.of(1, 2).toArray();
  }
}
