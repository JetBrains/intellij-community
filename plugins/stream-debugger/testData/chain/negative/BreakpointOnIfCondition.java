import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    if (args.length == 0) {
      final int s = IntStream.range(1, 2).sum();
    } else {
      final int s = IntStream.range(1, 5).sum();
    }
  }
}
