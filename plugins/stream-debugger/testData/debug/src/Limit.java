import java.util.stream.Stream;

public class Limit {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Object[] res = Stream.of(1, 2, 3).limit(1).toArray();
  }
}
