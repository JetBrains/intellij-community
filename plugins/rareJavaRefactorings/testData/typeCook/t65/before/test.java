class Map<T, X> {
  T t;
  X x;
}

class Test {
    void foo() {
      Map x = new Map();

      x.t = "";
      x.x = new Integer(3);
    }
}