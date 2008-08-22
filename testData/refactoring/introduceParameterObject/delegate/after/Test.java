class Test {
    void foo(int... i) {
        foo(new Param(i));
    }

    void foo(Param param) {
    if (param.getI().lenght == 0) {
    }
  }

  void bar(){
    foo(1, 2);
  }
}