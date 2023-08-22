interface I<T> {}

class Pair<X> implements I<X>{
  X t;
}

class Test {
  Pair pair;

  <X> X foo (I<? extends X> i){return null;}

  void bar(){
    String u = (String) foo (pair);
  }
}