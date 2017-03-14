import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
    if (args.length == 0) {
      final int s = IntStream.range(1, 2).sum();
<caret>    } else {
      final int s = IntStream.range(1, 5).sum();
    }
  }
}
