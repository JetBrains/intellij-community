public class RearrangementTest19 {
  RearrangementTest19() {
  }

  void overloadedMethod1(int x, int y, int z) {
  }

  RearrangementTest19(int x, int y) {
  }

  void unoverloadedMethod() {
  }

  void overloadedMethod1(int x) {
    overloadedMethod1(x, 0, 0);
  }

  void overloadedMethod1(int x, int y) {
    overloadedMethod1(x, y, 0);
  }

  RearrangementTest19(int oneParam) {
  }
}
