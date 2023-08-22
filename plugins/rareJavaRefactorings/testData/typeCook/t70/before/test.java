class External{
  static void f (List x){
  }
}

class List<T, E> {
  T t;

  void set(T t, E e){
    this.t = t;
  }
}

class Test {

    void f (){
      List x = new List();
      x.set("", "");
      External.f(x);
    }
}