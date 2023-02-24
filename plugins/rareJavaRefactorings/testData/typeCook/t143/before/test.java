interface I<T> {}

class Pair<X> {
  void foo (I<? extends X> i){}
}

public class Test {
  Pair pair;

  void bar(){
    pair.foo (new I<I> ());
  }
}