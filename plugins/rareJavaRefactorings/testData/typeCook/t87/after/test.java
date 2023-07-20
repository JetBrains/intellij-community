class List<T> {
  T t;
}

class Test {
  void foo (){
    List x = new List();

    int [] y = (int[]) x.t;
  }
}
