public class MoveMethodTest {
  void <caret>foo (MoveMethodTest f) {
    foo(f);
    f.foo(f);
  }
}