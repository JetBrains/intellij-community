import java.util.Optional;
import java.util.stream.Stream;

public class FindAnyMappingNone {
  public static void main(String[] args) {
    // Breakpoint!
    final Optional<Object> res = Stream.empty().findAny();
  }
}