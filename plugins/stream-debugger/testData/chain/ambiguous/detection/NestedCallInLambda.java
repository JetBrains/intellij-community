import java.util.function.Consumer;
import java.util.stream.Stream;

public class NestedCallInLambda {
  public static void main(String[] args) {
    Stream.of(1)
      .peek(x -> {
<caret>        Stream.of(1).count()
      }).forEach(x -> {
    });
  }
}
