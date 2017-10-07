import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Baz {
  public static void main(String[] args) {
<caret>    final long res = Stream.of(1,2,3)
      .mapToInt(Integer::intValue)
      .map(x -> x * x)
      .boxed()
      .mapToDouble(Integer::doubleValue)
      .mapToObj(x -> new Object())
      .flatMapToLong(x -> LongStream.range(0, 10))
      .sum();
  }
}
