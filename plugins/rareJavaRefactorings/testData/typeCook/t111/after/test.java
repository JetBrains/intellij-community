class List<T> {
  <A> List<A> f (List<A> x){
    return x;
  }
  T t;
}

class Test {
  void foo (){
    List x = null;
    List y = x.f(x);

    x.t = "";
  }
}
