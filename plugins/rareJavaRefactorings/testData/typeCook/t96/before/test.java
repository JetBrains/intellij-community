class List<T> {
  T t;
}

class A {}
class B extends A {}

class Test{
  void foo(){
     List<List> x = new List<List>();
     List<List> y = new List<List>();

     x.t.t = "";
     y.t = x.t;
     y.t.t = new Integer(3);
  }
}