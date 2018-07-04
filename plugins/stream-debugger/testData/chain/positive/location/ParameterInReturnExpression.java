import java.util.stream.IntStream;

public class Baz {
  static int bar(int a, int b, int c) {
    return 0;
  }

  static int foo() {
<caret>    return bar(0, 1, IntStream.range(1, 2).sum());
  }

  public static void main(String[] args) {
    foo();
  }
}
