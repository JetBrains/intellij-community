import java.util.stream.Stream;

public class AccessToPrivateClassWithMethodReference {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.generate(MyClass::new).mapToInt(x -> x.field).limit(10).count();
  }

  private static class MyClass {
    private int field = 10;

    public MyClass() {
    }
  }
}
