import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final int res = IntStream.of(1, 2, 3).mapToObj(x -> new Object()).mapToInt(x -> 1).sum();
  }
}
