import java.util.stream.IntStream;

public class StreamTerminatorParameter {
  public static void main(String[] args) {
<caret>    IntStream.of(1, 2).reduce(IntStream.of(1, 2, 3).sum(), (l, r) -> l + r);
  }
}
