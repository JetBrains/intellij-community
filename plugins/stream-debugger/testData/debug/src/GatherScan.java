import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class GatherScan {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3, 4, 5).gather(Gatherers.scan(() -> "", (string, number) -> string + number)).toArray();
  }
}
