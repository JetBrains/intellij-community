public abstract class Foo<T> {
  private T name;

  public Foo(T name) {
    this.name = name;
  }

  public abstract void bar();

  public static void test() {
    new Foo<String>("Name") {
      @Override public void bar() {
      }
    }.bar();
  }
}
