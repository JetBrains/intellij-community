import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class GatherCustomTakeWhile {
  public static void main(String[] args) {
    Gatherer<Integer, ?, Integer> gatherer = Gatherer.<Integer, Integer>of((state, element, downstream) -> {
      if (element >= 3) {
        return false;
      }
      return downstream.push(element);
    });
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3, 4).gather(gatherer).toArray();
  }
}
