import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    Object[] val = Stream.of(1, 2, 3).skip(2).toArray();
  }
}
