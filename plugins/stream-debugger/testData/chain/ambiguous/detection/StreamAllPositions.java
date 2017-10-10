import java.util.stream.IntStream;

public class StreamAllPositions {
  public static void main(String[] args) {
<caret>    IntStream.of(IntStream.of(1).sum()).skip(IntStream.of(1).count()).reduce(IntStream.of(1, 2).sum(), (l, r) -> l + r);
  }
}
