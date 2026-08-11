import java.util.stream.Stream;

public class MapToNull {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(1).map(x -> null).filter(x -> x != null).findAny();
  }
}
