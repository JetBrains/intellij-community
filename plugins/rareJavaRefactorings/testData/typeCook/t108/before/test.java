class List<T> {
  <A> List<A> f (List<A> x){
    return x;
  }
}

class Test {
  void foo (){
    List x = null;
    List<String> y = null;

    x = x.f(y);
  }
}
