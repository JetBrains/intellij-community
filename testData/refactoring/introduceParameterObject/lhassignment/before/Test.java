class Test {
  void foo(int i) {
    if (i == 0) {
        i = 9;
    }
  }

  void bar(){
    foo(1);
  }
}