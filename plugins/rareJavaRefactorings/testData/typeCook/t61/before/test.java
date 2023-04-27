class OhYeah<Z> {
  Z t;
}

class Coll <T, X extends OhYeah<T>> {
  X f;
}

class Test {
    void foo() {
      Coll x;
      x.f.t = new Integer(2);
    }
}