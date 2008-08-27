class D {
  void foo(){}

  void bar(A a){
      a.getMyField().foo();
  }

  void bazz(Test t){
      t.getMyField().foo();
  }
}