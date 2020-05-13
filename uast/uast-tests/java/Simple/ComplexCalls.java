class A extends B{

  A(){super();}

  A(int a){
    super(a);
  }

  void foo() {}
}

class C extends A {}

class B {

  B(int a){
  }

  B(){this(1);}

  A a = new A();

  C c = new C();

  public A bar() {
    return new A();
  }

  public A barR(String a) {
    return bar();
  }

  public static void main(String[] args) {

    B b = new B(1);
    b.bar().barR("asdf").bar().foo();
    b.bar().bar().bar().foo();
    b.a.a.a.bar().a.foo();

  }
}