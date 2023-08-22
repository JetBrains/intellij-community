import lombok.RequiredArgsConstructor;

public class ClassWithLombokConstructor {

  @RequiredArgsConstructor
  public static class A {
    final int i;
    final String s;
  }

  public static class B extends A {
    public B() {
      <error descr="Default constructor doesn't exist">super()</error>;
    }

    public B(int i) {
      this(i, "");
    }

    public B(int i, String s) {
      super(i, s);
    }
  }
}
