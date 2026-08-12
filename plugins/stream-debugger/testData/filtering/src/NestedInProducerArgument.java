import java.util.List;
import java.util.stream.Stream;

public class NestedInProducerArgument {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(Stream.of(1, 2, 3).toList(), Stream.of(4, 5, 6).map(x -> x + 1).toList())
      .flatMap(List::stream)
      .count();
  }
}
