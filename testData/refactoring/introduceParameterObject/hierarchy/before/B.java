class B extends Test {
  void foo(int i) {
    System.err.println(--i);
  }

  void bar() {
    foo(1);
  }
}