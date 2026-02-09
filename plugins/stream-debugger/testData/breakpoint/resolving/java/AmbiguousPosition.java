import java.util.stream.Stream;

public class AmbiguousPosition {
  public static void main(String[] args) {
    Stream.of(1, 2).count() + <caret>Stream.of(3, 4).count();
  }
}
