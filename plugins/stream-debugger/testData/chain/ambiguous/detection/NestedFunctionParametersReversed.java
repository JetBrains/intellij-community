import java.util.stream.Stream;

public class NestedFunctionParametersReversed {
  private static long fun(long a, long b) {
    return a + b;
  }

  public static void main(String[] args) {
<caret>    long c = fun(fun(Stream.of(1, 2, 3).count(), Stream.of(1, 2, 3, 4).count()), Stream.of(1, 2).count());
  }
}
