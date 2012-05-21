public class RearrangerTest13 {
// Preceding comment: TL=GF
// MN=GF
// AM=
// Level 1
  void GF() { // grandfather
    F1();
    F2();
  }
// Trailing comment: TL=GF
// MN=GF
// AM=
// Level 1
// Preceding comment: TL=GF
// MN=F1
// AM=GF()
// Level 2

  private void F1() {
    S1B();
    S1A();
  }
// Trailing comment: TL=GF
// MN=F1
// AM=GF()
// Level 2
// Preceding comment: TL=GF
// MN=F2
// AM=GF()
// Level 2

  private void F2() {
    S2A();
    S2B();
  }
// Trailing comment: TL=GF
// MN=F2
// AM=GF()
// Level 2
// Preceding comment: TL=GF
// MN=S1B
// AM=GF().[F1(),F2()]
// Level 3

  void S1B() {
  }
// Trailing comment: TL=GF
// MN=S1B
// AM=GF().[F1(),F2()]
// Level 3
// Preceding comment: TL=GF
// MN=S1A
// AM=GF().[F1(),F2()]
// Level 3

  void S1A() {
  }
// Trailing comment: TL=GF
// MN=S1A
// AM=GF().[F1(),F2()]
// Level 3
// Preceding comment: TL=GF
// MN=S2A
// AM=GF().[F1(),F2()]
// Level 3

  void S2A() {
  }
// Trailing comment: TL=GF
// MN=S2A
// AM=GF().[F1(),F2()]
// Level 3
// Preceding comment: TL=GF
// MN=S2B
// AM=GF().[F1(),F2()]
// Level 3

  void S2B() {
  }
// Trailing comment: TL=GF
// MN=S2B
// AM=GF().[F1(),F2()]
// Level 3
}
