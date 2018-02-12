import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
    final Stream<Integer> a = Stream.empty();
    final Stream<Integer> b = Stream.of(1);
<caret>    List<Integer> val = Stream.concat(a, b).collect(Collectors.toList());
  }
}
