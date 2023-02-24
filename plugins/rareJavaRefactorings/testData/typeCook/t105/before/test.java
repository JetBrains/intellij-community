class List<T> {
  <A extends T> void f (A x){
  }
}

class Mist<X> {
  List<X> x;
}

class Test {
  void foo (){
    Mist x = null;
    x.x.f("");
  }
}
