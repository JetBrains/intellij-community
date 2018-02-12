import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    Stream.of(1).forEachOrdered(x -> {});
  }
}
