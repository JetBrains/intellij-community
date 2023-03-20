class List<T> {
  <A extends List<T>> void f (A a){
  }
}

class Test {
  void foo (){
    List x = null;
    List<String> y = null;
    x.f(y);
  }
}
