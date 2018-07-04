import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    {
      final int s = IntStream.range(1, 2).sum();
    }
  }
}
