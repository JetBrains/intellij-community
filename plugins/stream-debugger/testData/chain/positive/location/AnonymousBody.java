import java.util.stream.Stream;

public class Baz {
  public static void main(String[] args) {
    new Runnable() {

      @Override
      public void run() {
<caret>        Stream.of(1,2,3).forEach(x -> {});
      }
    }.run();
  }
}
