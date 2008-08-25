class Test<T> {
  T myT;
  void foo(T t){}
  void bar(){
    foo(myT);
  }
}