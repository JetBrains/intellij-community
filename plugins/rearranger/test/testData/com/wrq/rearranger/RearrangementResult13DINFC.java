public class RearrangerTest13 {
  void GF() { // grandfather
    F1();
    F2();
  }

  private void F1() {
    S1B();
    S1A();
  }

  void S1B() {
  }

  void S1A() {
  }
// Preceding comment: TL=GF
// MN=GF
// AM=GF()
// Level 2

  private void F2() {
    S2A();
    S2B();
  }

  void S2A() {
  }

  void S2B() {
  }
}
