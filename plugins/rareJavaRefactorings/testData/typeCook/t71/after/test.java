class List<T> {
  T t;

  void set(T t){
    this.t = t;
  }
}

class Test {

  static List x = new List();

  static void f (){
    x.set("");
  }
}

class External{
  static List f (){
    return Test.x;
  }
}
