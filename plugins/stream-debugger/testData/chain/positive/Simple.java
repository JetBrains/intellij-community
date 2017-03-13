package foo;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public static void bar() {
<caret>    List<Integer> values = Stream.of(1, 2, 3).collect(Collectors.toList());
  }
}
