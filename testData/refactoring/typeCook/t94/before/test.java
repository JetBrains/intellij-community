class List<T> {
  T t;
}

class Test{
  void foo(){
     List x = new List();
     List y = new List();

     (true ? x : y).t = "";
  }
}