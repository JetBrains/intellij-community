public class Foo {
  private int number;

  public Foo(int number) {
    this.number = number;
  }

  public static void test() {
    new Foo(10);
  }
}
