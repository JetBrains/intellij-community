class OfList<B> {
  B t;

  Set<B> set;
}

class Iterator<C> {
  C get;
}

class Set<D> {
  Iterator<D> iterator;
}

class Test {
  void foo (){
    OfList x = new OfList();
    x.t = "";
    Iterator i = x.set.iterator;
  }
}
