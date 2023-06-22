class List<T> {
  <A extends T> List(A x){
    t = x;
  }

  T t;
}

class Test {
  void foo (){
    List y = new List("");
  }
}
