import java.util.stream.Stream;

public class NoneMatchMappingTrue {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final boolean res = Stream.of(1, 2, 3).noneMatch(x -> x == 5);
  }
}