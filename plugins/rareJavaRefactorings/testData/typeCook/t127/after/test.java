class A {
}

class B extends A {
}

class Collection<E>{
}

class List<T> extends Collection<T> {
  List(Collection<T> a){
  }

  T t;
}

class Convertor{
   static <X> List<X> asList (X[] x){
    return null;
   }
}

class Test {

  void f() {
    A[] a = null;
    List b = new List(Convertor.asList(a));
    B c = null;
    b.t = c;
  }

}
