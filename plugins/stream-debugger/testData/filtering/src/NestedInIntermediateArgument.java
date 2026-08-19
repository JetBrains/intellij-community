import java.util.stream.Stream;

public class NestedInIntermediateArgument {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Stream.of(7, 8, 9).limit(Stream.of(10, 11, 12).count()).count();
  }
}
