import java.util.function.Function;
import java.util.stream.Stream;

public class ExceptionInProducerCall {
  public static void main(String[] args) {
    try {
      check();
    } catch (Throwable ignored) {
    }
  }

  private static void check() {
    //Breakpoint!
    Stream.generate(() -> {
      throw new RuntimeException();
    }).map(Function.identity()).toArray();
  }
}
