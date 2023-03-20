class OhYeah<T,Q>{
  T t;
}

class Coll <X, Y> {
  X f;
  Y s;
  OhYeah<Y,Coll<X,X>> a;
}



class Test {

    void foo() {
        Coll x;
        x.f = "";
        x.a.t = new Integer(3);
    }
}