class List<T> {
  T t;
}

class A {}
class B extends A {}

class Test{
  void foo(){
     List x = new List<A>();
     List y = new List<B>();

     x = y;
  }
}