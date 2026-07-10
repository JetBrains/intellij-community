import java.util.stream.Stream;

public class MethodReference {
  public static void main(String[] args) {
<caret>    Stream.of("a", "b", "c")
      .map(String::toUpperCase)
      .toList();
  }
}
