import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class GatherCustomBufferThenFlush {
  public static void main(String[] args) {
    Gatherer<Integer, List<Integer>, Integer> gatherer = new Gatherer<>() {
      @Override
      public Supplier<List<Integer>> initializer() {
        return ArrayList::new;
      }

      @Override
      public Integrator<List<Integer>, Integer, Integer> integrator() {
        return Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
          state.add(element);
          return true;
        });
      }

      @Override
      public BiConsumer<List<Integer>, Downstream<? super Integer>> finisher() {
        return (state, downstream) -> {
          for (Integer value : state) {
            downstream.push(value);
          }
        };
      }
    };
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3).gather(gatherer).toArray();
  }
}
