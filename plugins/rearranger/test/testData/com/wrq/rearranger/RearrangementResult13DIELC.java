public class RearrangerTest13 {
// Preceding comment: TL=GF
// MN=GF
// AM=
// Level 1
  void GF() { // grandfather
    F1();
    F2();
  }
// Preceding comment: TL=GF
// MN=GF
// AM=GF()
// Level 2

  private void F1() {
    S1B();
    S1A();
  }
// Preceding comment: TL=GF
// MN=F1
// AM=GF().F1()
// Level 3

  void S1B() {
  }

  void S1A() {
  }

// Trailing comment: TL=GF
// MN=F1
// AM=GF().F1()
// Level 3

  private void F2() {
    S2A();
    S2B();
  }
// Preceding comment: TL=GF
// MN=F2
// AM=GF().F2()
// Level 3

  void S2A() {
  }

  void S2B() {
  }

// Trailing comment: TL=GF
// MN=F2
// AM=GF().F2()
// Level 3

// Trailing comment: TL=GF
// MN=GF
// AM=GF()
// Level 2

// Trailing comment: TL=GF
// MN=GF
// AM=
// Level 1
}
