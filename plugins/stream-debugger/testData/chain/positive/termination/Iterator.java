import java.util.Iterator;
import java.util.stream.Stream;

public class Baz {
  public static void bar() {
<caret>    Iterator<Integer> it = Stream.of(1).iterator();
  }
}
