class List<T> {
  static <A, B extends List<A>> void f(A x, B y){
  }
}

class Test {
  void foo (){
    List y = null;

    List.f("", y);
  }
}
