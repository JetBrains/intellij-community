class List<T> {
  <A> void f (T a){
  }
}

class Test {
  void foo (){
    List x = null;
    x.f("");
  }
}
