class Test {
    D f;
    void foo(D c) {}
}

class B extends Test {
    void foo(D c) {
      f = c;
    }
}

class C {}
class D extends C{}