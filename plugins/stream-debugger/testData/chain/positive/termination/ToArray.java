import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    final Integer[] list = Stream.of(1).toArray(Integer[]::new);
  }
}
