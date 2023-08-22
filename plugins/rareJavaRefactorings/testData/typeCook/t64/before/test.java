class OhYeah<Z>{
  Z z;
}

class Coll <T, X extends OhYeah<T>> {
  T t;
  X x;
  X f() {return null;};
}

class Test {
    void foo() {
      Coll x;

      x.f().z = "";
    }
}