import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    List<Integer> val = Stream.iterate(1, i -> i + 1).collect(Collectors.toList());
  }
}
