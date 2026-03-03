import java.util.stream.Stream;

public class Nulls {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(null, null).toArray();
  }
}
