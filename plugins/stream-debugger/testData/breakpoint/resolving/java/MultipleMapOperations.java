import java.util.stream.Stream;

public class MultipleMapOperations {
  public static void main(String[] args) {
<caret>    Stream.of(1, 2, 3)
      .map(x -> x * 2)
      .map(x -> x + 1)
      .map(x -> x * 3)
      .toList();
  }
}
