import java.util.stream.IntStream;

public class Baz {
  public static void foo(int bar) {
  }

  public static void main(String[] args) {
<caret>    foo(IntStream.of(1, 2, 3).sum());
  }
}
