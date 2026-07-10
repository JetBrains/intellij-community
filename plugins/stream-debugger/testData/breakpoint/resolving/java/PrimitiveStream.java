import java.util.stream.IntStream;

public class PrimitiveStream {
  public static void main(String[] args) {
<caret>    IntStream.of(1, 2, 3)
      .map(x -> x * 2)
      .sum();
  }
}
