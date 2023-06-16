interface I<T> {}

class Pair<X> {
  void goo (I<? super I<X>> i){}
}

public class Test {
  Pair pair;

  void bar(){
    pair.goo (new I<I> ());
  }
}