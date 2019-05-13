import java.util.stream.IntStream;

public class Baz {
  public static int foo(int baz, int bar) {
    return IntStream.range(1, 2).sum();
  }

  public static void main(String[] args) {
<caret>    foo(0, IntStream.of(1, 2, 3).sum());
  }
}
