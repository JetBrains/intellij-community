import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    int count = IntStream.of(1).count();
  }
}
