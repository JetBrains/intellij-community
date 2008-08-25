class Test {
  Test(){}

  void foo(int... f) {}

  void bar() {
    foo(1, 2);
  }
}