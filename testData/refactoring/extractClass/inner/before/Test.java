class Test {
  void bar() {
     new Inner().foo();
  }

  static class Inner {
    void foo(){}
  }
}