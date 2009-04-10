class Super {
  void foo() {
    Super s = new Super();
    s.bar();
  }

  static int bar(){return 1;}
}