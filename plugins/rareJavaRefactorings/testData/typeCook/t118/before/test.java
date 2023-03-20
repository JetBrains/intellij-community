class List<T> {
  static <A, B extends List<A>> void f(A x, B y){
  }
  T t;
}

class Mist<T> extends List<T>{
}

class Test {
  void foo (){
    List y = null;
    Mist z = null;

    List.f(y.t, z);

    z.t = "";
  }
}
