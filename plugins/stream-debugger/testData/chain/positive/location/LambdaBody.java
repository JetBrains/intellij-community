import java.util.stream.Stream;

public class Baz {
  public static void main(String[] args) {
<caret>    ((Runnable) () -> Stream.of(1, 2, 3).forEach(x -> {})).run();
  }
}
