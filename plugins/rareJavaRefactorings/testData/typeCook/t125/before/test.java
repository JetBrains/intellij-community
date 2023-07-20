class Map<A, B>{
  A a;
  B b;
}

class List<C>{
}

class Test {
  void f() {
    List<Integer> x = null;
    List<String> y = null;

    Map z = null;

    z.a = "";
    z.b = x;
    z.b = y;
  }
}
