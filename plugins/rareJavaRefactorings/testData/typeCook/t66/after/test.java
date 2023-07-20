class Map<T, X> {
  T t;
  X x;
}

class Test {
    void foo() {
      Map x = new Map();
      Map y = x;

      x.t = "";
      x.x = new Integer(3);
    }
}