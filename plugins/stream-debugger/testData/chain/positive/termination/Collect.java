import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    final List<Integer> list = Stream.of(1).collect(Collectors.toList());
  }
}
