class List<A> {
  A t;

  Set<A> set(){
    return null;
  }
}

class OfList<B> extends List<B> {

}

class Iterator<C> {
  C get (){
    return null;
  }
}

class Set<D> {
  Iterator<D> iterator(){
    return null;
  }
}

class Test {
  void foo (){
    OfList x = new OfList();
    x.t = "";
    Iterator i = x.set().iterator();
  }
}
