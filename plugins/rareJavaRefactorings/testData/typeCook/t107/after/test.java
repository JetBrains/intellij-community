class List<T> {
  <A extends T> A[] f (A[] x){
    return x;
  }
}

class Test {
  void foo (){
    List x = null;
    String[] y = (String[]) x.f(new String[] {});
  }
}
