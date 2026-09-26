import java.util.Optional;
import java.util.stream.Stream;

public class FindFirstMappingPresent {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final Optional<Integer> res = Stream.of(1, 2, 3).findFirst();
  }
}
