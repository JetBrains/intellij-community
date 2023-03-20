class Coll <T, X> {
  T t;
  X x;
  Coll<X, T> f() {return null;};
}

class Test {
    void foo() {
      Coll x;

      x.f().t = "";
      x.t = new Integer(4);
    }
}