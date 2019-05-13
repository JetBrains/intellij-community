import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public class FindAnyMappingPresent {
  public static void main(String[] args) {
    // Breakpoint!
    final Optional<Integer> res = Stream.of(1, 2, 3).findAny();
  }
}
