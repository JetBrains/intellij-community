import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class MinMappingNone {
  public static void main(String[] args) {
    // Breakpoint!
    final Optional<Integer> res = Stream.of(1).skip(1).max(Comparator.comparingInt(x -> -x));
  }
}