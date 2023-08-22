package some.pack.age;

@lombok.ToString
public class ToStringSimpleClassName {
  int x;
  String name;

  @lombok.ToString
  static class InnerStaticClass {
    String  someProperty;
  }

  @lombok.ToString
  class InnerClass {
    String someProperty;
  }
}
