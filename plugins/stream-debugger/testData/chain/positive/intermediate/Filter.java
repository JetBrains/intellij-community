import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    List<Integer> val = Stream.of(1, 2, 3).filter(x -> x % 2 == 1).collect(Collectors.toList());
  }
}
