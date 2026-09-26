import java.util.Optional;
import java.util.stream.Stream;

public class MapNullToValue {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final Optional<Object> any = Stream.of(null, null).map(x -> new Object()).findAny();
  }
}
