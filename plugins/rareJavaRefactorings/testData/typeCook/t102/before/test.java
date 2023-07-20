class List<T> {
  <A> void f (A a){
  }
}

class Test {
  void foo (){
    List x = null;
    x.f("");
  }
}
