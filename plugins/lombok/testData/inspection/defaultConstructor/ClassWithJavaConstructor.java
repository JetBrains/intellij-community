public class ClassWithJavaConstructor {
  public static class A {
    final int i;
    final String s;

    public A(int i, String s) {
      this.i = i;
      this.s = s;
    }
  }

  public static class B extends A {
    public B() {
      super<error descr="Expected 2 arguments but found 0">()</error>;
    }

    public B(int i) {
      this(i, "");
    }

    public B(int i, String s) {
      super(i, s);
    }
  }
}
