class List<T> {
  <A extends List<T>> void f (A a){
  }
}

class Mist extends List<String>{
}

class Test {
  void foo (){
    Mist y = null;
    List x = null;
    x.f(y);
  }
}
