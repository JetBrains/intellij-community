import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class GatherFold {
  public static void main(String[] args) {
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3, 4, 5).gather(Gatherers.fold(() -> "", (string, number) -> string + number)).toArray();
  }
}
