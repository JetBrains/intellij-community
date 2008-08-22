class Test {
  void foo(Param param) {
      param.setI(param.getI() + 1);
      if (param.getI() == 0) ;
  }

  void bar(){
    foo(new Param(1));
  }
}