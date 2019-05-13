import java.util.stream.Stream;

public class AccessToPrivateMethodsInStaticContext {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.generate(() -> 1).limit(10).peek(x -> method(x)).filter(x -> x % 2 == 0).count();
  }

  private static boolean method(int x) {
    return true;
  }
}
