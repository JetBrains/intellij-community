import java.util.stream.Stream;

public class DistinctHardCase {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    final long res = Stream.of(new Integer(1), new Integer(2), new Integer(1)).distinct().count();
  }
}
