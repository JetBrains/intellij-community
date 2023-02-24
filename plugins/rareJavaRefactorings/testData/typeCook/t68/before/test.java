class Map<T, X> {
  T t;
  X x;

  int size(){
    return 0;
  }
}

class Test {
    void foo() {
      Map x = new Map();
      Map y = x;

      x.t = "";
      x.x = new Integer(3);

      Test[] t = new Test[x.size()];
    }
}