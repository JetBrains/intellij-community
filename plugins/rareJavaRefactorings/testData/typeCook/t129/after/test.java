class A {}
class B extends A {}

class Collection<T>{
  T t;
}

class Set<X> extends Collection<X>{

}

class Test {
  void g(Collection ancestors) {
    A a = (A) ancestors.t;
  }

  void f() {
    Set x = null;
    x.t = new B();
    g(x);
  }
}
