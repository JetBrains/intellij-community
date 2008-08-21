class Test<R> {
  void foo(Param<R> param) {
    if (param.getR() == null) {
    }
  }

  void bar(R r){
    foo(new Param<R>(r));
  }
}