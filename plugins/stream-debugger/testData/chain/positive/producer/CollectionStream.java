import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Baz {
  public static void bar() {
    Collection<Integer> collection = Arrays.asList(1,2,3);
<caret>    List<Integer> values = collection.stream().collect(Collectors.toList());
  }
}
