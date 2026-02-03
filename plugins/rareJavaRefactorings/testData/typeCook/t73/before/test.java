class List<T> {
  T t;
}

class Test {
  void foo (){
     List y = new List();
     List x = new List();

     x.t = y.t;
     y.t = x.t;

     x.t = "";
     y.t = new Integer(3);
  }
}
