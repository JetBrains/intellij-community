import java.util.function.IntPredicate;

public class Baz {
  private static class IntStream {
    public static IntStream of(Integer... a) {
      return new IntStream();
    }

    public IntStream filter(IntPredicate predicate) {
      return this;
    }

    public int sum() {
      return 0;
    }
  }

  public static void main(String[] args) {
<caret>    final int s = IntStream.of(1, 2).filter(x -> x % 2 == 0).sum();
  }
}
