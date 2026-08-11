import java.util.function.Supplier;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class GatherCustomLimit {
  public static void main(String[] args) {
    Gatherer<Integer, int[], Integer> gatherer = new Gatherer<>() {
      @Override
      public Supplier<int[]> initializer() {
        return () -> new int[1];
      }

      @Override
      public Integrator<int[], Integer, Integer> integrator() {
        return (state, element, downstream) -> {
          downstream.push(element);
          state[0]++;
          return state[0] < 2;
        };
      }
    };
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3, 4).gather(gatherer).toArray();
  }
}
