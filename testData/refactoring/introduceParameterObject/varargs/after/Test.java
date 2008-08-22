class Test {
  void foo(Param param) {
    if (param.getI().lenght == 0) {
    }
  }

  void bar(){
    foo(new Param(1, 2));
  }
}