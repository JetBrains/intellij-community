class Coll <T> {
  T t;
  Coll x;
}

class Test {
    void foo() {
      Coll x;
      x.x.t = "";
    }
}