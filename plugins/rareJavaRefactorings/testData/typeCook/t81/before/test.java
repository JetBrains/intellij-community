class List<T> {
  T t;
}

class Test {
  void foo (){
    List[] y = new List [10];
    List x = y[0];

    y[1].t = "";
    x.t = new Integer(2);
  }
}
