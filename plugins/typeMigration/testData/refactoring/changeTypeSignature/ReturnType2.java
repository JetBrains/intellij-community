class A<T> {
  T foo(){
    return null;
  }
}

class B extends A<S<caret>tring> {
  String foo(){return super.foo();}

  void bar() {
    foo();
    if (foo() == null) return;
  }
}