import java.util.stream.IntStream;

public class NestedStreamTerminatorCallParameter {
  public static void main(String[] args) {
<caret>    IntStream.of(1, 2, 3)
      .reduce(IntStream.of(1, 2)
                .reduce(IntStream.of(1, 2).sum(),
                        (left, right) -> left + right),
              (left, right) -> left + right);
  }
}
