public class A {
  void test(int <caret>i) {
    int j = i;
  }

  void callTest() {
    test(0);
  }
}