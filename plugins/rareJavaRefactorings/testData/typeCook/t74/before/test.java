class List<T> {
  T t;
}

class Test {
  void foo (){
     //List<List> y = new List<List>();
     List<List> x = new List<List>();

     //x.t = y.t;
    // y.t = x.t;

     x.t.t = "";
     //y.t.t = new Integer(3);
  }
}
