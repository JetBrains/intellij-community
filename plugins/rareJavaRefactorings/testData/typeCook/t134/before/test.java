interface List<T>{}

class ArrayList<P> implements List<P>{}

class Pair<X, Y>{}

class C {}
class A {}
class B extends A{}

class Test {
  void buildAllMaps(){
    List<Pair<B, C>> methods = new ArrayList<Pair<B, C>>();
    ArrayList s = null;
    g(methods);
  }

  <T> void g(List<Pair<T, C>> list) {
  }
}
