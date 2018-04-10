import java.util.function.Supplier;

public class Baz {
  private static Supplier<Runnable> getRunnableSupplier() {
    return
        <caret>() -> () -> {};
  }

  public static void main(String[] args) {
  }
}
