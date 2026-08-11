import java.util.stream.Stream;

public class ComplexLambda {
  public static void main(String[] args) {
<caret>    Stream.of(1, 2, 3)
      .map(x -> {
        int y = x * 2;
        return y + 1;
      })
      .toList();
  }
}
