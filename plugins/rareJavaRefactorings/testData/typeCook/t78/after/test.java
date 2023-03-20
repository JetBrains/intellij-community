class List<A> {
  A t;

  Set<A> set;
}

class OfList<B> extends List<B> {

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
