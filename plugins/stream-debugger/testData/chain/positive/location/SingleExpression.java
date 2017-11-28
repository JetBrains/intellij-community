import java.util.stream.IntStream;

public class Baz {
  public static void main(String[] args) {
<caret>    IntStream.range(1,2).forEach(x -> {});
  }
}
