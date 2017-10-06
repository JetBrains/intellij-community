import java.util.stream.Stream;

public class SimpleExpression {
  public static void main(String[] args) {
<caret>    long c = Stream.of(1, 2).count() + Stream.of(1).count();
  }
}
