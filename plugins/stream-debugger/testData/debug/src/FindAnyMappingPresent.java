import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public class FindAnyMappingPresent {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final Optional<Integer> res = Stream.of(1, 2, 3).findAny();
  }
}
