class D {
  void foo(){}

  void bar(A a){
    a.foo();
  }

  void bazz(Test t){
      t.getMyField().foo();
  }
}