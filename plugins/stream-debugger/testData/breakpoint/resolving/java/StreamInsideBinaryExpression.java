import java.util.stream.Stream;

public class StreamInsideBinaryExpression {
  public static void main(String[] args) {
    int a = 1 + <caret>Stream.of(1, 2).count();
  }
}
