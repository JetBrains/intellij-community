class List<T> {
  T t;
}

class Super {
   void f (List x){
   }
}

class Test extends Super {
  void f (List x){
     x.t = "";
  }

  void g (List x){
     x.t = "";
  }
}

class Sub extends Test{
  void g (List x){
  }
}
