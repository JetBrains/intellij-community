import java.util.stream.Stream;

public class NestedStreams {
  public static void main(String[] args) {
<caret>    Stream.of(1, 2, 3)
      .map(x -> Stream.of(x, x * 2).toList())
      .toList();
  }
}
