class Test{
  void fo<caret>o(int i){
    i++;
  }

  void bar(Test t){
    t.foo(1);
  }
}