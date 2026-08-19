import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class GatherMapConcurrent {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).gather(Gatherers.mapConcurrent(1, number -> number + 1)).toArray();
  }
}
