import java.util.Arrays;
import java.util.stream.Stream;

public class DeeplyNestedStreams {
  public static void main(String[] args) {
    Integer[] arr = {1, 2, 3, 4, 5};
    // Breakpoint! lambdaOrdinal(-1)
    Arrays.stream(arr).limit(Stream.of(1, 2).count() + Stream.of(3, 4).count()).map(x -> x * x).filter(x -> x % 2 == 0).limit(Stream.of(1, 2).toList().size()).toList();
  }
}
