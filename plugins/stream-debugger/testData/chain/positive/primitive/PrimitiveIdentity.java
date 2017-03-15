import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final long res = IntStream.of(1, 2, 3).filter(x -> x % 2 == 0).count();
  }
}
