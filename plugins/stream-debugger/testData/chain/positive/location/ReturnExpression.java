import java.util.stream.IntStream;

public class Baz {
  public static int foo() {
<caret>    return IntStream.range(1, 2).sum();
  }

  public static void main(String[] args) {
    foo();
  }
}
