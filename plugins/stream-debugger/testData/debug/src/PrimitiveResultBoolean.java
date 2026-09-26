import java.util.stream.Stream;

public class PrimitiveResultBoolean {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    boolean res = Stream.of(1).anyMatch(x -> x == 2);
  }
}
