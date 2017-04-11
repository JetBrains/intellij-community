import java.util.stream.Stream;

public class AccessToPrivateMethods {
  public static void main(String[] args) {
    new AccessToPrivateMethods().test();
  }

  private void test() {
    // Breakpoint!
    Stream.generate(() -> 1).limit(10).filter(x -> method(x)).mapToInt(x -> x * 2).count();
  }

  private boolean method(int x) {
    return true;
  }
}
