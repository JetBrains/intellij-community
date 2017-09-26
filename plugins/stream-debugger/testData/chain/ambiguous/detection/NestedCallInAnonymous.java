import java.util.function.Consumer;
import java.util.stream.Stream;

public class NestedCallInAnonymous {
  public static void main(String[] args) {
    Stream.of(1)
      .peek(new Consumer<Integer>() {
        @Override
        public void accept(Integer x) {
<caret>          Stream.of(1).count();
        }
      }).forEach(x -> {
    });
  }
}
