class List<T> {
  T t;
}

class Mist<Q> extends List {
}

class Test {
  void foo (List y){
    y.t = "";
    Mist z = (Mist) y;
  }
}
