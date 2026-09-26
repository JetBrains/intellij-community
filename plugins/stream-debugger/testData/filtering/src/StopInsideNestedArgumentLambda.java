import java.util.stream.Stream;

public class StopInsideNestedArgumentLambda {
  public static void main(String[] args) {
    // `filter` and not `map` on purpose: `count()` is allowed to skip the pipeline entirely when the number of elements
    // is known from the source (see the `Stream.count()` javadoc), and `map` keeps it known - the lambda would never be
    // called and the breakpoint below would never be hit. `filter` may drop elements, so `count()` does call it.
    Stream.of(1, 2).limit(Stream.of(3, 4).filter(x -> {
      // Breakpoint!
      return x > 0;
    }).count()).count();
  }
}
