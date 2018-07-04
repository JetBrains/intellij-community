import java.util.stream.LongStream;

public class Baz {
  public static void bar() {
<caret>    LongStream.of(1, 2, 3).sum();
  }
}
