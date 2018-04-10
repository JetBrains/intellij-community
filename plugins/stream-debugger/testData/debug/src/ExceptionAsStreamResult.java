import java.util.stream.Stream;

public class ExceptionAsStreamResult {
  public static void main(String[] args) {
    // Breakpoint!
    Throwable res = Stream.of(new Throwable()).reduce(new Throwable(), (l, r) -> l);
  }
}
