class List<T> {
  T t;

  void set(T t){
    this.t = t;
  }
}

class Test {
  void foo (){
     List y = new List();

     y.set("");

     List[] x = new List[] {y};
  }
}
