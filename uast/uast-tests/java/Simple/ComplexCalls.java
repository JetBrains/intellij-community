class A extends B{
  void foo() {}
}

class B {

  A a = new A()


  public A bar() {
    return new A();
  }

  public A barR(String a) {
    return bar()
  }

  public static void main(String[] args) {

    B b = new B();
    b.bar().barR("asdf").bar().foo();
    b.bar().bar().bar().foo();
    b.a.a.a.bar().a.foo();

  }
}