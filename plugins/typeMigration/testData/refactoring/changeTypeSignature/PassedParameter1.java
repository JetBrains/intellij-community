class A<T> {
  void foo(T t){}
  void bar(T t, int i){}
}

class B extends A<S<caret>tring>{
  void foo(String t) {
    super.foo(t);
  }

  void bar(String t, int i){
    foo(t);
    int k = i;
    super.bar(t, k);
  }

  void bar1(String s) {
    foo(s);
  }
}