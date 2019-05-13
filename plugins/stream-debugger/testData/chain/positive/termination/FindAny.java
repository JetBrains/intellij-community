import java.util.Optional;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    final Optional<Integer> first = Stream.of(1).findAny();
  }
}
