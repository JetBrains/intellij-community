class List<T> {
  T t;
}

class Test {
  void foo (){
    List x = new List();

    x.t = new int[3];
  }
}
