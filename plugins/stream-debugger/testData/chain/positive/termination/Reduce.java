import java.util.Optional;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    Optional<Integer> res = Stream.of(1).reduce((i1, i2) -> i1 + i2);
  }
}
