class Coll <X, Y> {
  X f;
  Y s;
  Coll<Y, X> a;
}

class Test {

    void foo() {
        Coll x;
        x.a.f = "";
        x.a.s = new Integer(3);
    }
}