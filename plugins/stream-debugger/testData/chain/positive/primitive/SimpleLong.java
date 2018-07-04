import java.util.stream.LongStream;

public class Baz {
  public static void main(String[] args) {
<caret>    final long res = LongStream.of(1).sum();
  }
}
