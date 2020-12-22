package some.pack.age;

public class ToStringSimpleClassName {
  int x;
  String name;

  static class InnerStaticClass {
    String someProperty;

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "ToStringSimpleClassName.InnerStaticClass(someProperty=" + this.someProperty + ")";
    }
  }

  class InnerClass {
    String someProperty;

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "ToStringSimpleClassName.InnerClass(someProperty=" + this.someProperty + ")";
    }
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public java.lang.String toString() {
    return "ToStringSimpleClassName(x=" + this.x + ", name=" + this.name + ")";
  }
}
