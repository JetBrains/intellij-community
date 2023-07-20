class Map<T, X> {
  T t;
  X x;

  T get(){
    return null;
  }

  int size(){
    return 0;
  }
}

class Test {

    void f (Map x){
      String i = (String) x.get();
    }

    void foo() {
      Map x = new Map();
      Map y = x;

      x.t = "";
      x.x = new Integer(3);

      f(x);
    }
}