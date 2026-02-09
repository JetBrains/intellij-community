import java.util.stream.Stream;

public class NestedStreamCaretOnInner {
  public static void main(String[] args) {
    Stream.of(1, 2, 3)
      .map(x -> <caret>Stream.of(x, x * 2).toList())
      .toList();
  }
}
