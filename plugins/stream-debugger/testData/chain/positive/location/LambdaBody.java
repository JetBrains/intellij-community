import java.util.stream.Stream;

public class Baz {
  public static void main(String[] args) {
    ((Runnable) () -> <caret>Stream.of(1, 2, 3).forEach(x -> {})).run();
  }
}
