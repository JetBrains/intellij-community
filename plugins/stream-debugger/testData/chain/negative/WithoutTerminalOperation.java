import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final IntStream range = IntStream.range(1, 2);
  }
}
