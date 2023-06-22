class List<T> {
  class Mist<A extends T>{
    A a;
  }

  T t;
  Mist m;
}

class Test{
  void foo(){
    List x = null;
    x.m.a = "";
  }
}