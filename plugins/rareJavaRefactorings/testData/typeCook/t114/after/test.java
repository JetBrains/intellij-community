class List<T> {
  void f(T[] x){
  }
}

class Test {
  void foo (){
    List y = null;

    y.f(new String[] {});
  }
}
