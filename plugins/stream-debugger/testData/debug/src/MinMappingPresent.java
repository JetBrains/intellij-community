import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class MinMappingPresent {
  public static void main(String[] args) {
    // Breakpoint!
    final Optional<Integer> res = Stream.of(1, 2, 3).min(Comparator.comparingInt(x -> -x));
  }
}
