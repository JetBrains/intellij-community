import lombok.NoArgsConstructor;

public class ClassWithLombokDefaultConstructor {

  @NoArgsConstructor
  public static class A {
    int i;
    String s;

    public A(int i, String s) {
      this.i = i;
      this.s = s;
    }
  }

  public static class B extends A {
    public B() {
      super();
    }

    public B(int i) {
      this(i, "");
    }

    public B(int i, String s) {
      super(i, s);
    }
  }
}
