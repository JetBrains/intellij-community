class Map<A, B>{
  A a;
  B b;
}

class List<C>{
}

class Test {
  void f() {
    Map<String, List<String>> x = null;
    Map<String, List<Integer>> y = null;

    Map z = null;

    z = x;
    z = y;
  }
}
