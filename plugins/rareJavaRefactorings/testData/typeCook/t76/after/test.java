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
  D d;
  Iterator<D> iterator(){
    return null;
  }
}

class Test {
  void foo (){
    OfList x = new OfList();
    Iterator i = x.set().iterator();
    Set u = (Set) i.get();
    u.d = "";
  }
}
