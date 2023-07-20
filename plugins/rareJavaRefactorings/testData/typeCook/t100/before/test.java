class List<T> {
  <A extends T> void f (A a){
  }
}

class Test {
  void foo (){
    List x = null;
    x.f("");
  }
}
