import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Baz {
  public static void bar() {
<caret>    List<Integer> val = IntStream.range(1, 10).boxed().collect(Collectors.toList());
  }
}
