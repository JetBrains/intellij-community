class List<T> {
  <A> List<A> f (A[] x){
    return x;
  }
}

class Test {
  void foo (){
    List x = null;
    x = x.f(new String[] {});
  }
}
