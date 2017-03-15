import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final int res = IntStream.of(1).sum();
  }
}
