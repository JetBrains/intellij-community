import java.util.stream.Stream;

public class QualifierChain {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(1, 2).map(x -> x + 1).toList().stream().filter(x -> x % 2 == 0).toList();
  }
}
