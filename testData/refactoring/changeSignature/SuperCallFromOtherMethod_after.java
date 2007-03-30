class A {
    void <caret>foo(int nnn) {
    }
}

class B extends A {
    void boo() {
        super.foo(-222);
    }
}
