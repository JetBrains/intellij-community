class List<P> {
  P p;
}

class Pair<X, Y>{
  X x;
  Y y;
}

class Test {
  void f(){
    List<Pair> a = null;

    a.p.x = "";
    a.p.y = new Integer(3);
  }
}
