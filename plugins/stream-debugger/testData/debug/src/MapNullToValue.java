import java.util.Optional;
import java.util.stream.Stream;

public class MapNullToValue {
  public static void main(String[] args) {
    // Breakpoint!
    final Optional<Object> any = Stream.of(null).map(x -> new Object()).findAny();
  }
}
