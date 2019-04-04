import java.util.stream.Stream;

public class AccessToPrivateClass {
  public static void main(String[] args) {
    new AccessToPrivateClass().test();
  }

  private void test() {
    // Breakpoint!
    Stream.generate(() -> new MyClass()).mapToInt(x -> x.field).limit(10).count();
  }

  private class MyClass {
    private int field = 10;

    public MyClass() {
    }
  }
}
