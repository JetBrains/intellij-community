class Test {
    C f;
    void foo(C c) {
      f = c;
    }
}

class B extends Test {
    void foo(C c) {

    }
}

class C {}
class D extends C{}