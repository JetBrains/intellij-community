import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class GatherCustomConsumeAll {
  public static void main(String[] args) {
    Gatherer<Integer, List<Integer>, Integer> gatherer = new Gatherer<>() {
      @Override
      public Supplier<List<Integer>> initializer() {
        return ArrayList::new;
      }

      @Override
      public Integrator<List<Integer>, Integer, Integer> integrator() {
        return Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
          // Consume every element into the buffer, but never push it downstream.
          state.add(element);
          return true;
        });
      }

      @Override
      public BiConsumer<List<Integer>, Downstream<? super Integer>> finisher() {
        // Unlike GatherCustomBufferThenFlush, the buffer is dropped: the finisher pushes nothing either.
        return (state, downstream) -> {
        };
      }
    };
    // Breakpoint! lambdaOrdinal(-1)
    Object[] result = Stream.of(1, 2, 3).gather(gatherer).toArray();
  }
}
