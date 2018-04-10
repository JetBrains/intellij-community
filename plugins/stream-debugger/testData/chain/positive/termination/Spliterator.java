import java.util.Spliterator;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    final Spliterator<Integer> spliterator = Stream.of(1).spliterator();
  }
}
