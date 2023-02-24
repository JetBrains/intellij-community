class List<P> {
  P p;
}

class Pair<X, Y>{
  X x;
  Y y;
}

class Test {
  void f(){
    List a = new List<Pair<String,Integer>> ();
  }
}
