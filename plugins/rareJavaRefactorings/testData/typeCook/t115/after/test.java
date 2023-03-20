class List<T> {
  T[] f(){
    return null;
  }
}

class Test {
  void foo (){
    List y = null;

    String[] x = (String[]) y.f();
  }
}
