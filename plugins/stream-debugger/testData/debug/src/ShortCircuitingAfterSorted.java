import java.util.stream.Stream;

public class ShortCircuitingAfterSorted {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(3, 2, 1).sorted().findFirst();
  }
}
