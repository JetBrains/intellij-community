import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class GatherCustomOneToManyPerElement {
  public static void main(String[] args) {
    Gatherer.Integrator.Greedy<Void, Integer, Integer> integrator = (state, element, downstream) -> {
      downstream.push(element);
      downstream.push(-element);
      return true;
    };
    Gatherer<Integer, Void, Integer> gatherer = Gatherer.of(integrator);
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3).gather(gatherer).toArray();
  }
}
