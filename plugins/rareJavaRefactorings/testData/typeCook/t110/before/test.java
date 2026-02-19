class List<T> {
  <A> List<A> f (A[] x){
    return x;
  }

  <B extends T> List(List<B> y){
  }
}

class Test {
  void foo (){
    List x = null;
    List y = new List(x.f(new String[] {}));
  }
}
