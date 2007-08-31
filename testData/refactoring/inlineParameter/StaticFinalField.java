public class A {
  void test(String[] <caret>value) {
    String[] myValue = value;
  }

  void callTest() {
    test(CONST);
  }

  void callTest2() {
    test(CONST);
  }

  public static final String[] CONST = new String[] { "A", "B" };
}