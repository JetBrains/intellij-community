class Test<G, R> {
  void foo(R r) {
    if (r == null) {
    }
  }

  void bar(R r){
    foo(r);
  }
}