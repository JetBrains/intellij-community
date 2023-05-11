class List<T> {
  T t;
}

interface A {
     List get();
}

class Test{

  void foo(){
    List x = new A() {
      public List get(){
        return new List<String>();
      }
    }.get();
  }
}