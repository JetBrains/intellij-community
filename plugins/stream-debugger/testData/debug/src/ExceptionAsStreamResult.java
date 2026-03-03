import java.util.stream.Stream;

public class ExceptionAsStreamResult {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Throwable res = Stream.of(new Throwable()).reduce(new Throwable(), (l, r) -> l);
  }
}
