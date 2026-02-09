import java.util.stream.Stream;

public class SingleElementOf {
  public static void main(String[] args) {
<caret>    Stream.of(1)
      .map(x -> x * 2)
      .toList();
  }
}
