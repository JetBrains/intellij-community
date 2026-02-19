class List<T> {
  T t;
}

class A extends List{
}

class Test {
  void foo (List y){
    y.t = "";
    A z = (A) y;
  }
}
