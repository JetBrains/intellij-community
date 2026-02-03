import java.util.function.Consumer;
import java.util.stream.Stream;

public class Bar {
  public static void main(String[] args) {
    final String var = "abd";
    final long count = Stream.of(va<caret>r, "acd", "ef").map(String::length).filter(x -> x % 2 == 0).peek(new Consumer<Integer>() {
      @Override
      public void accept(Integer x) {
        System.out.println(x);
      }
    }).count();
  }
}
