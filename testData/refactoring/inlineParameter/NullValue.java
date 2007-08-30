public class A {
  void test(int count, String <caret>s) {
    String t = s;
  }

  void callTest() {
    test(1, null);
  }

  void callTest2() {
    test(2, null);
  }
}