class List<T> {
  T t;
}

class Test {
  Test(List x){
  }

  void foo (){
    List x = null;
    x.t = "";
    Test y = new Test(x) {};
  }
}
