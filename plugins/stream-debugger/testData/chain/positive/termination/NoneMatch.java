import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    boolean res = Stream.of(1).noneMatch(x -> x % 2 == 0);
  }
}
