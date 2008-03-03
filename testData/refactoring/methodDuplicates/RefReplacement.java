class Test {
  static void <caret>main(Test t){
    t.foo();
  }

  void foo(){}
}

class Test1 {
  void bar(Test t) {
    t.foo();
  }
}