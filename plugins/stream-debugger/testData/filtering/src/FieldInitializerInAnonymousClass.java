import java.util.stream.Stream;

public class FieldInitializerInAnonymousClass {
  public static void main(String[] args) {
    Runnable runnable = new Runnable() {
      // Breakpoint!
      final long streamSize = Stream.of(1, 2).map(x -> x).count();

      @Override
      public void run() {
        System.out.println(streamSize);
      }
    };
    runnable.run();
  }
}
