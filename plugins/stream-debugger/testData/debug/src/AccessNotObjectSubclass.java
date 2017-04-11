import java.util.function.Consumer;
import java.util.stream.Stream;

public class AccessNotObjectSubclass {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1, 2, 3).peek(new Super() {
      @Override
      public void accept(Integer integer) {
        System.out.println(new MyClass().toString());
      }
    }).count();
  }

  private static class MyClass {
  }

  static abstract class Super implements Consumer<Integer> {
  }
}
