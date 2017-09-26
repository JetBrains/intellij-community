import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    Object[] val = IntStream.of(1,2,3).boxed().toArray();
  }
}
