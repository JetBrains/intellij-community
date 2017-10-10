import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    double s = Stream.of(1, 2, 3).mapToDouble(x -> x * x).sum();
  }
}
