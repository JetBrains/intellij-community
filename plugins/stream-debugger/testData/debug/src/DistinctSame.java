import java.util.stream.Stream;

public class DistinctSame {
  public static void main(String[] args) {
    final Object obj = new Object();
    // Breakpoint! lambdaOrdinal(-1)
    final long res = Stream.of(obj, obj, obj).distinct().count();
  }
}
