class OfList<B> {
  B t;

  Set<B> set(){
    return null;
  }
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
