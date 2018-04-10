import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Baz {
  private static Stream<Integer> foo() {
    return Stream.of(1, 2, 3);
  }

  public static void bar() {
<caret>    List<Integer> val = foo().collect(Collectors.toList());
  }
}
