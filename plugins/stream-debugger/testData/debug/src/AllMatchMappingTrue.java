import java.util.stream.Stream;

public class AllMatchMappingTrue {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final boolean res = Stream.of(1, 2, 3).allMatch(x -> x > 0);
  }
}